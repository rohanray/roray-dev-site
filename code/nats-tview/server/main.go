package main

import (
	"encoding/json"
	"log"
	"net/http"
	"time"

	natsServer "github.com/nats-io/nats-server/v2/server"
)

type Server struct {
	ID   int    `json:"id"`
	Name string `json:"name"`
	IP   string `json:"ip"`
	Desc string `json:"desc"`
}

var servers []*Server

func main() {
	sa := &Server{
		ID:   1,
		Name: "Server 1",
		IP:   "10.11.11.11",
		Desc: "This is server 1",
	}
	servers = append(servers, sa)
	sb := &Server{
		ID:   2,
		Name: "Server 2",
		IP:   "10.12.12.12",
		Desc: "This is server 2",
	}
	servers = append(servers, sb)
	opts := &natsServer.Options{
		Port: 4222,
	}
	ns, err := natsServer.NewServer(opts)
	if err != nil {
		log.Fatal(err)
	}
	go ns.Start()
	// defer ns.WaitForShutdown()
	if !ns.ReadyForConnections(10 * time.Second) {
		log.Fatal("Embedded NATS server not ready for connections")
	}
	log.Println("Embedded NATS server started on port", ns.Addr().String())

	srv := &http.Server{
		Addr:    ":8181",
		Handler: routes(),
	}
	log.Println("HTTP server started on port :", srv.Addr)
	err = srv.ListenAndServe()
	log.Fatal(err)
}

func routes() http.Handler {
	mux := http.NewServeMux()
	mux.HandleFunc("GET /servers", func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("Content-Type", "application/json")
		w.WriteHeader(http.StatusOK)
		json.NewEncoder(w).Encode(servers)
	})
	mux.HandleFunc("POST /servers", func(w http.ResponseWriter, r *http.Request) {
		var server Server
		if err := json.NewDecoder(r.Body).Decode(&server); err != nil {
			http.Error(w, err.Error(), http.StatusBadRequest)
			return
		}
		servers = append(servers, &server)
		w.Header().Set("Content-Type", "application/json")
		w.WriteHeader(http.StatusCreated)
		json.NewEncoder(w).Encode(server)
	})
	return mux
}
