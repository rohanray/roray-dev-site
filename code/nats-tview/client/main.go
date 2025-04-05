package main

import (
	"client/api"
	"encoding/json"
	"fmt"
	"log"
	"net/http"
	"time"

	"github.com/nats-io/nats.go"
	"github.com/rivo/tview"
	"google.golang.org/protobuf/proto"
)

type Server struct {
	ID   int    `json:"id"`
	Name string `json:"name"`
	IP   string `json:"ip"`
	Desc string `json:"desc"`
}

var (
	app     *tview.Application
	svl     *tview.List
	content tview.Primitive
	servers []*Server
	wlcBox  *tview.Box
	flex    *tview.Flex
)

func main() {
	nc, err := nats.Connect("nats://127.0.0.1:4222")
	if err != nil {
		log.Fatal(err)
	}
	defer nc.Close()

	app = tview.NewApplication()
	wlcBox = tview.NewBox().SetBorder(true).SetTitle(" Welcome ")
	flex = tview.NewFlex()
	flex.AddItem(wlcBox, 0, 1, true)

	sDC := make(chan []*Server)
	go GetServers(sDC)
	go func() {
		app.QueueUpdateDraw(func() {
			servers = <-sDC
			svl = tview.NewList()
			svl.SetTitle(" Servers ").SetBorder(true)
			content = tview.NewBox().SetBorder(true).SetTitle(" Server Base Stats ")
			for idx, s := range servers {
				svl.AddItem(s.Name, s.IP, rune('1'+idx), func() {
					flex.RemoveItem(content)
					cpuTxt := tview.NewTextView().SetText("CPU: 0%")
					memTxt := tview.NewTextView().SetText("Memory: 0%")
					diskTxt := tview.NewTextView().SetText("Disk: 0%")
					content = tview.NewFlex().SetDirection(tview.FlexRow).
						AddItem(tview.NewTextView().SetText("Server IP: "+s.IP), 0, 1, false).
						AddItem(tview.NewTextView().SetText("Server Base Stats"), 0, 1, false).
						AddItem(cpuTxt, 0, 1, false).
						AddItem(memTxt, 0, 1, false).
						AddItem(diskTxt, 0, 1, false)
					flex.AddItem(content, 0, 3, false)
					go func() {
						_, err := nc.Subscribe("host.stats.1", func(msg *nats.Msg) {
							rcvData := &api.BaseStatsReply{}
							err = proto.Unmarshal(msg.Data, rcvData)
							if err != nil {
								log.Fatal(err)
							}
							app.QueueUpdateDraw(func() {
								cpuTxt.SetText(fmt.Sprintf("CPU: %.2f %%", *rcvData.Cpu))
								diskTxt.SetText(fmt.Sprintf("Disk: %.2f %%", *rcvData.Disk))
								memTxt.SetText(fmt.Sprintf("Memory: %.2f %%", *rcvData.Mem))
							})
						})
						if err != nil {
							log.Fatal(err)
						}
					}()
				})
			}
			flex.Clear()
			flex.SetDirection(tview.FlexColumn).
				AddItem(svl, 0, 1, true).
				AddItem(content, 0, 3, false)
			app.SetFocus(svl)
		})
	}()

	if err := app.SetRoot(flex, true).Run(); err != nil {
		log.Fatal(err)
	}
}

func GetServers(ch chan []*Server) {
	time.Sleep(2 * time.Second)
	resp, err := http.Get("http://localhost:8181/servers")
	if err != nil {
		log.Fatal(err)
	}
	defer resp.Body.Close()
	if resp.StatusCode != http.StatusOK {
		log.Fatalf("Error: %s", resp.Status)
	}
	if err := json.NewDecoder(resp.Body).Decode(&servers); err != nil {
		log.Fatal(err)
	}
	ch <- servers
}
