# 🚀 CryptoPulse — Pure Java Cryptocurrency Platform Clone

A lightweight, zero-dependency full-stack **Cryptocurrency Tracking & Portfolio Management Platform** built entirely with **Pure Java (Standard JDK)** for the backend and **HTML5, CSS3, and JavaScript** for the frontend. 

This project demonstrates how to build a complete RESTful micro-service architecture, reverse API proxy, and interactive dashboard without relying on heavy external frameworks like Spring Boot, Jackson, or Maven/Gradle.

---

## 🔑 Key Features

- 📈 **Live Market Data:** Fetches real-time price updates, market capitalization, 24-hour volume, and market dominance via the CoinGecko API.
- ⭐ **Watchlist Management:** Pin favorite assets to a personalized watchlist with real-time updates.
- 💼 **Portfolio Tracker:** Calculate total portfolio value, entry prices, asset allocation, and real-time Profit/Loss ($ and %).
- 📊 **Interactive Charts:** Detailed modal views featuring interactive 7-day price trend lines powered by `Chart.js`.
- 👤 **User Authentication:** Built-in registration and login system with user-isolated watchlists and portfolios.
- 🔍 **Instant Search & Filtering:** Client-side real-time filtering across top global cryptocurrencies.
- 🎨 **Modern UI/UX:** Dark-mode glassmorphism interface styled with glowing violet accents and fully responsive layouts.

---

## 🛠️ Tech Stack & Architecture

```text
[ Browser (HTML/CSS/JS) ] ── (REST API / JSON) ──► [ Pure Java HTTP Server ] ──► [ CoinGecko API ]
                                                          │
                                                [ In-Memory Storage ]
