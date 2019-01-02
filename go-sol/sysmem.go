package main

func TotalMemory() uint64 {
	return sysTotalMemory()
}

func FreeMemory() uint64 {
	return sysFreeMemory()
}
