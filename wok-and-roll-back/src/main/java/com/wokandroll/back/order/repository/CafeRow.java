package com.wokandroll.back.order.repository;

import java.time.LocalTime;

/** A café as the order needs it: its opening hours decide the slots, its capacity the places. */
public record CafeRow(
    long id,
    String name,
    String address,
    LocalTime opensAt,
    LocalTime closesAt,
    int slotCapacity) {}
