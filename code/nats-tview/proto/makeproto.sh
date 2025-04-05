#!/bin/bash

echo " "
echo "Generating proto files..."
echo " "

echo "protoc --go_out=. panmon_agent.proto"
protoc --go_out=. panmon_agent.proto
echo "PB files generated successfully."

cp panmon_agent.pb.go ../server/api
cp panmon_agent.pb.go ../agent/api
cp panmon_agent.pb.go ../client/api
echo "PB files copied to server, agent, and client directories."