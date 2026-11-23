package com.wokandroll.back.order.service;

import com.wokandroll.back.order.dto.OrderResponse;

/** The placed order, and whether it is a replay of an earlier request with the same key. */
public record OrderResult(OrderResponse order, boolean replayed) {}
