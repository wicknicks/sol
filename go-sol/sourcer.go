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

func sysInfo() {
	in := &syscall.Sysinfo_t{}
	err := syscall.Sysinfo(in)
	if err != nil {
		fmt.Printf("%v\n", err)
		return
	}

	fmt.Printf("Sys info: %v\n", toJson(*in))
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
	Timestamp    int64   `json:"epoch"`
}

func main() {
	su := SystemUsage{
		NumCpus:     NumCpu(),
		CpuUsage:    CpuUsage(),
		TotalMemory: TotalMemory(),
		FreeMemory:  FreeMemory(),
		Timestamp: time.Now().UnixNano() / 1000000,
	}

	su.AvailPercent = 100 * float32(su.FreeMemory) / float32(su.TotalMemory)

	fmt.Printf("%s", toJson(su))

	return

	broker := "localhost:9092"
	group := "agroup"
	topics := append([]string{}, "_confluent-metrics")
	sigchan := make(chan os.Signal, 1)
	signal.Notify(sigchan, syscall.SIGINT, syscall.SIGTERM)

	c, err := kafka.NewConsumer(&kafka.ConfigMap{
		"bootstrap.servers":    broker,
		"group.id":             group,
		"session.timeout.ms":   6000,
		"default.topic.config": kafka.ConfigMap{"auto.offset.reset": "earliest"}})

	if err != nil {
		fmt.Fprintf(os.Stderr, "Failed to create consumer: %s\n", err)
		os.Exit(1)
	}

	fmt.Printf("Created Consumer %v\n", c)

	err = c.SubscribeTopics(topics, nil)

	run := false

	for run == true {
		select {
		case sig := <-sigchan:
			fmt.Printf("Caught signal %v: terminating\n", sig)
			run = false
		default:
			ev := c.Poll(100)
			if ev == nil {
				continue
			}

			switch e := ev.(type) {
			case *kafka.Message:
				fmt.Printf("%% Message on %s:\n%s\n",
					e.TopicPartition, string(e.Value))
			case kafka.PartitionEOF:
				fmt.Printf("%% Reached %v\n", e)
			case kafka.Error:
				fmt.Fprintf(os.Stderr, "%% Error: %v\n", e)
				run = false
			default:
				fmt.Printf("Ignored %v\n", e)
			}
		}
	}

	fmt.Printf("Closing consumer\n")
	c.Close()
}
