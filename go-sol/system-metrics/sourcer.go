package main

import (
	"encoding/json"
	"fmt"
	"github.com/confluentinc/confluent-kafka-go/kafka"
	"os"
	"os/exec"
	"os/signal"
	"runtime"
	"strconv"
	"strings"
	"syscall"
	"time"
	"net"
	"reflect"
)

func NumCpu() int {
	return runtime.NumCPU()
}

func CpuUsage() float32 {
	out, err := exec.Command("ps", "-A", "-o", "%cpu").Output()
	if err != nil {
		fmt.Printf("could not run cpu usage command %v\n", err)
		return 0.0
	}

	susages := strings.Split(string(out), "\n")
	if len(susages) <= 1 {
		return 0.0
	}

	sum := float32(0.0)
	for ix, u := range susages {
		tu := strings.TrimSpace(u)
		if ix == 0 || len(tu) == 0 {
			continue
		}

		s, err := strconv.ParseFloat(tu, 32)
		if err != nil {
			fmt.Printf("Could not parse err=%v\n", err)
			continue
		}
		sum += float32(s)
	}

	return sum
}

func toJson(in interface{}) string {
	b, err := json.Marshal(in)
	if err != nil {
		fmt.Printf("Could not marshall object %v\n", err)
		return ""
	} else {
		return string(b)
	}
}

type SystemUsage struct {
	NumCpus      int     `json:"num_cpus"`
	CpuUsage     float32 `json:"cpu_usage_percent"`
	TotalMemory  uint64  `json:"total_memory_bytes"`
	FreeMemory   uint64  `json:"free_memory_bytes"`
	AvailPercent float32 `json:"free_memory_percent"`
	Timestamp    int64   `json:"ts_millis"`
}

type SolLogger struct {
	AppName    string            `json:"app_name"`
	LoggerName string            `json:"logger_name"`
	Host       map[string]string `json:"host"`
	Type       string            `json:"type"`
}

func GetOutboundIP() string {
    conn, err := net.Dial("udp", "8.8.8.8:80")
    if err != nil {
        fmt.Printf("%v\n", err)
    }
    defer conn.Close()

    localAddr := conn.LocalAddr().String()
    ix := strings.Index(localAddr, ":")
    if ix >= 0 {
    	localAddr = localAddr[0:ix]
    }
	return localAddr
}

func main() {

	hostname, _ := os.Hostname()
	ip := GetOutboundIP()

	solLogger := SolLogger {
		AppName: "SolSystemMetrics",
		LoggerName: "CpuMemoryMetrics",
		Host: map[string]string{
			"name": hostname,
			"addr": ip,
		},
		Type: "example_go_logger",
	}
	fmt.Printf("Identifying as: %v\n", toJson(solLogger))

	broker := "localhost:9092"
	group := "agroup"
	command_topics := append([]string{}, "sol-commands")
	logs_topics := "sol-logs"
	sigchan := make(chan os.Signal, 1)
	signal.Notify(sigchan, syscall.SIGINT, syscall.SIGTERM)

	commandsChannel := make(chan []byte)
	run := true
	produce_metrics := false

	go func() {
		c, err := kafka.NewConsumer(&kafka.ConfigMap{
		"bootstrap.servers":    broker,
		"group.id":             group,
		"session.timeout.ms":   6000,
		"default.topic.config": kafka.ConfigMap{"auto.offset.reset": "earliest"}})

		if err != nil {
			fmt.Fprintf(os.Stderr, "Failed to create consumer: %s\n", err)
			run = false
			return
		}

		err = c.SubscribeTopics(command_topics, nil)
		fmt.Println("Consumer for command topic initialized")

		for run == true {
			ev := c.Poll(1000)
			if ev == nil {
				continue
			}

			switch e := ev.(type) {
			case *kafka.Message:
				var cLogger SolLogger
				err := json.Unmarshal(e.Key, &cLogger)
				if err != nil {
					fmt.Println("error:", err)
				}
				if reflect.DeepEqual(solLogger, cLogger) {
					commandsChannel <- e.Value
				} else {
					fmt.Printf("Request for a different logger %v\n", cLogger)
					fmt.Printf("Registered as %v\n", solLogger)
				}
			case kafka.OffsetsCommitted:
				
			default:
				fmt.Printf("Ignored %s %v\n", reflect.TypeOf(e).String(), e)
			}
		}

		fmt.Printf("Closing consumer\n")
		c.Close()
	}()

	go func() {
		producer, err := kafka.NewProducer(&kafka.ConfigMap{"bootstrap.servers": broker})

		if err != nil {
			fmt.Printf("Failed to create producer: %s\n", err)
			run = false
			return
		}

		for run == true {

			if produce_metrics {

				su := SystemUsage{
					NumCpus:     NumCpu(),
					CpuUsage:    CpuUsage(),
					TotalMemory: TotalMemory(),
					FreeMemory:  FreeMemory(),
					Timestamp:   time.Now().UnixNano() / 1000000,
				}
				su.AvailPercent = 100 * float32(su.FreeMemory) / float32(su.TotalMemory)

				err = producer.Produce(&kafka.Message{
					TopicPartition: kafka.TopicPartition{Topic: &logs_topics, Partition: kafka.PartitionAny},
					Value:          []byte(toJson(su)),
				}, nil)			

				if err != nil {
					fmt.Printf("Could not produce metric %v\n", err)
				}

			}

			time.Sleep(5 * time.Second)
		}
	} ()

	for run == true {
		select {
		case sig := <-sigchan:
			fmt.Printf("Caught signal %v: terminating\n", sig)
			run = false
		case cmd := <- commandsChannel:
			var c map[string]string
			err := json.Unmarshal(cmd, &c)
			if err != nil {
				fmt.Println("error:", err)
			} 
			if c["status"] == "enabled" {
				fmt.Println("Enabling producing metrics")
				produce_metrics = true
			} else if c["status"] == "disabled" {
				fmt.Println("Disabling producing metrics")
				produce_metrics = false
			} else {
				fmt.Printf("Could not grok command %v\n", string(cmd))
			}
		}
	}
}
