+++
title = "A Console Routine on Channeling NATS in Golang"
date = "2025-04-05"
description = "Sharing my initial learnings using NATS, Golang Channels & Tview."

[taxonomies]
tags = ["golang", "nats", "tui"]
+++

## TL;DR

This article will go through high level usage & example of [NATS - an open source messaging system](https://nats.io/), [TVIEW - Golang based Terminal UI library](https://github.com/rivo/tview), concurrency in Golang using Channels and Goroutines.

## What

I have recently started working on a Golang project of my own to develop something similar to [Uptime Kuma](https://uptime.kuma.pet/) which is a self-hosted monitoring tool. I believe it's a perfect project with the right complexity to learn advanced concepts of Go.

![Short video showcasing nats and golang channels with goroutines](/img/channel-nats.gif)

## Why

1. Learn advanced concepts in Golang with real-life projects
2. _(Try to)_ Build a better Golang based open-source alternative to https://uptime.kuma.pet
3. To pique my interest in terminal user interface (TUI)
