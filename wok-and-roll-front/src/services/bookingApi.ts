// Table booking (API contract §3.10, #183).
import { requestJson } from './api';
import { num, record, str } from './parse';

export interface BookingRequest {
  cafeId: number;
  date: string;
  time: string;
  guests: number;
  contactName: string;
  contactPhone: string;
  contactEmail?: string | null;
  comment?: string | null;
}

export interface BookingResponse {
  bookingNumber: string;
  status: 'CONFIRMED' | 'CANCELLED';
  createdAt: string;
  cafe: { id: number; name: string; address: string };
  date: string;
  time: string;
  endTime: string;
  guests: number;
  tableSeats: number;
  contactName: string;
}

export const BOOKING_RULES = {
  durationMinutes: 90,
  stepMinutes: 30,
  minLeadMinutes: 60,
  maxDaysAhead: 30,
  minGuests: 1,
  maxGuests: 8,
} as const;

export async function bookTable(
  request: BookingRequest,
  idempotencyKey: string,
): Promise<BookingResponse> {
  const source = 'Bookings API';
  const body = record(
    await requestJson(
      '/api/v1/bookings',
      {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', 'Idempotency-Key': idempotencyKey },
        body: JSON.stringify(request),
      },
      201,
    ),
    source,
  );
  const cafe = record(body.cafe, source, 'cafe');
  return {
    bookingNumber: str(body, 'bookingNumber', source),
    status: str(body, 'status', source) as BookingResponse['status'],
    createdAt: str(body, 'createdAt', source),
    cafe: {
      id: num(cafe, 'id', source),
      name: str(cafe, 'name', source),
      address: str(cafe, 'address', source),
    },
    date: str(body, 'date', source),
    time: str(body, 'time', source),
    endTime: str(body, 'endTime', source),
    guests: num(body, 'guests', source),
    tableSeats: num(body, 'tableSeats', source),
    contactName: str(body, 'contactName', source),
  };
}
