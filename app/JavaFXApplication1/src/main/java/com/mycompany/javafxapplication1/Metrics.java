/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.mycompany.javafxapplication1;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;
/**
 *
 * @author ntu-user
 */
public final class Metrics {

    private static final ConcurrentHashMap<String, LongAdder> counts = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, LongAdder> totalNanos = new ConcurrentHashMap<>();

    private Metrics() {}

    public static long start() {
        return System.nanoTime();
    }

    public static void end(String operation, long startNanos) {
        long duration = System.nanoTime() - startNanos;

        counts.computeIfAbsent(operation, k -> new LongAdder()).increment();
        totalNanos.computeIfAbsent(operation, k -> new LongAdder()).add(duration);
    }

    public static String snapshot() {
        StringBuilder sb = new StringBuilder();
        sb.append(" Performance metrics \n");

        for (String op : counts.keySet()) {
            long c = counts.get(op).sum();
            long t = totalNanos.getOrDefault(op, new LongAdder()).sum();

            double avgMs = (c == 0) ? 0.0 : (t / 1_000_000.0) / c;

            sb.append(String.format("%s | count=%d | avg=%.2fms\n", op, c, avgMs));
        }

        return sb.toString();
    }
    public static String getSummary() {
        return snapshot();
    }
}
