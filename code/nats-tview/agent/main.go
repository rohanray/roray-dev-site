package main

import (
	"agent/api"
	"log"
	"time"

	"github.com/nats-io/nats.go"
	"github.com/shirou/gopsutil/cpu"
	"github.com/shirou/gopsutil/disk"
	"github.com/shirou/gopsutil/mem"
	"google.golang.org/protobuf/proto"
)

func main() {
	nc, err := nats.Connect("nats://127.0.0.1:4222")
	if err != nil {
		log.Fatal(err)
	}
	defer nc.Close()
	log.Println("Connected to NATS server at", nc.ConnectedUrl())
	for c := time.Tick(1 * time.Second); ; <-c {
		v, _ := mem.VirtualMemory()
		cpuPercent, _ := cpu.Percent(0, false)
		diskUsage, _ := disk.Usage("/")

		stats := &api.BaseStatsReply{
			Cpu:  &cpuPercent[0],
			Mem:  &v.UsedPercent,
			Disk: &diskUsage.UsedPercent,
		}
		msg, err := proto.Marshal(stats)
		if err != nil {
			log.Fatal(err)
		}
		err = nc.Publish("host.stats.1", msg)
		if err != nil {
			log.Fatal(err)
		}
		log.Printf("Published message to NATS server, #%v", stats)
	}
}
