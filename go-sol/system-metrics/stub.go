// +build !darwin,!linux

package main

func sysTotalMemory() uint64 {
	return 0
}

func sysFreeMemory() uint64 {
	return 0
}
