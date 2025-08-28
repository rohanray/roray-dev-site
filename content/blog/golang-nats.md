+++
title = "A Console Routine on Channeling NATS. Let's GO!"
date = "2025-04-05"
description = "Sharing my initial learnings using NATS, Golang Channels & Tview."

[taxonomies]
tags = ["golang", "nats", "tui", "backend"]

[extra]
isso = true
+++

# TL;DR

This article is a short example of [NATS](https://nats.io/) - an open source messaging system, [TVIEW](https://github.com/rivo/tview) - Golang based Terminal UI library, concurrency in Golang using Channels and Goroutines. The source code can be found on [Github](https://github.com/rohanray/roray-dev-site/tree/main/code/nats-tview).
<br>
<br>

# What

I have recently started working on a personal Golang project to develop something similar to [Uptime Kuma](https://uptime.kuma.pet/) which is a self-hosted monitoring tool. I believe it's a perfect project with the right complexity to learn advanced concepts of Go.

![Short video showcasing nats and golang channels with goroutines](/img/channel-nats.gif)

The project code in [Github](https://github.com/rohanray/roray-dev-site/tree/main/code/nats-tview) has 4 directories:

1. **Server** - The central server which stores servers & resource details, configs etc
2. **Client** - TUI based native client app to manage resources that need to be monitored. Also gives TUI based views into historical/real-time monitoring data.
3. **Agent** - Remote light-weight agent running on target host servers responsible for shipping telemetry data to the central server
4. **Proto** - [Protobuf](https://protobuf.dev/) based API contracts between server, client & agent. 
<br>
<br>

## NATS

NATS is a simple, secure and performant communications system and data layer for digital systems, services and devices. It's used in this example project for pub-sub messaging (shipping) telemetry data from target remote servers to the central server. 

### The NATS server

To keep things simple for this example, we are using an embedded NATS server which is setup in the central server as below:

```go,linenos,server/main.go
opts := &natsServer.Options{
		Port: 4222,
	}
ns, err := natsServer.NewServer(opts)
if err != nil {
    log.Fatal(err)
}
go ns.Start()
```
_Line #2_  denotes that the embedded NATS server should run on port 4222.

_Line #8_  `go ns.Start()`  The go keyword creates a new goroutine, which is a lightweight thread managed by the Go runtime. Goroutines are a fundamental part of Go's concurrency model.

When this line executes, `ns.Start()` runs concurrently with the rest of the program. The main execution doesn't wait for this method to complete but continues immediately to the next line. Such pattern is commonly used when launching background services, servers, or long-running processes that need to operate independently of the main program flow.
<br>
<br>

### Publishing NATS message

Let's look at the agent code

```go,linenos,agent/main.go
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
```

__Line 2__ `nats.Connect("nats://127.0.0.1:4222")` initializes client connection to the NATS server.

__Line 22__ `nc.Publish("host.stats.1", msg)` publishes a single Protobuf serialized message containing the host base stats viz. CPU utilization, Memory utilization & Disk Usage.

# Why

1. Learn advanced concepts in Golang with real-life projects
2. _(Try to)_ Build a better Golang based open-source alternative to https://uptime.kuma.pet
3. To pique my interest in terminal user interface (TUI)
