const supabase = require('../config/db');
const { sendBookingConfirmationEmail, sendBookingCancellationEmail } = require('./emailService');

/**
 * Creates a booking after checking for time conflicts.
 * Emails are sent asynchronously; a failure won't roll back the booking.
 */
const createBooking = async (userId, bookingData) => {
  const { hall_id, purpose, date, start_time, end_time, participants, requirements } = bookingData;

  // ── Enforce booking hours: 9:00 AM – 10:00 PM (09:00 – 22:00) ─────────────
  if (start_time < '09:00' || start_time > '22:00') {
    throw new Error('Start time must be between 9:00 AM and 10:00 PM');
  }
  if (end_time < '09:00' || end_time > '22:00') {
    throw new Error('End time must be between 9:00 AM and 10:00 PM');
  }
  if (start_time >= end_time) {
    throw new Error('End time must be after start time');
  }

  // ── Conflict check: any confirmed booking overlapping this time? ────────────
  // Overlap condition: existing.start < new.end  AND  existing.end > new.start
  const { data: conflicts, error: conflictError } = await supabase
    .from('bookings')
    .select('id, start_time, end_time')
    .eq('hall_id', hall_id)
    .eq('date', date)
    .eq('status', 'confirmed')
    .lt('start_time', end_time)
    .gt('end_time', start_time);

  if (conflictError) throw new Error('Failed to check booking conflicts');
  if (conflicts && conflicts.length > 0) {
    throw new Error(
      `Hall already booked for selected time (${conflicts[0].start_time}–${conflicts[0].end_time})`
    );
  }

  // ── Create booking ─────────────────────────────────────────────────────────
  const { data: booking, error: insertError } = await supabase
    .from('bookings')
    .insert({
      user_id: userId,
      hall_id,
      purpose: purpose.trim(),
      date,
      start_time,
      end_time,
      participants: parseInt(participants, 10),
      requirements: requirements ? requirements.trim() : null,
      status: 'confirmed'
    })
    .select()
    .single();

  if (insertError) throw new Error('Failed to create booking. Please try again.');

  // ── Fetch related data for email ───────────────────────────────────────────
  const [{ data: hall }, { data: user }] = await Promise.all([
    supabase.from('halls').select('*').eq('id', hall_id).single(),
    supabase.from('users').select('*').eq('id', userId).single()
  ]);

  // ── Send confirmation email (non-blocking) ─────────────────────────────────
  sendBookingConfirmationEmail(user, booking, hall).catch((e) =>
    console.error('Confirmation email failed:', e.message)
  );

  return { booking, hall };
};

/**
 * Returns bookings for a user with optional search, status filter, and sorting.
 */
const getUserBookings = async (userId, query = {}) => {
  const { search, status, sort_by = 'date', sort_order = 'desc' } = query;

  let req = supabase
    .from('bookings')
    .select('*, hall:halls(id, name, floor, location)')
    .eq('user_id', userId)
    .order(sort_by, { ascending: sort_order === 'asc' });

  if (status) req = req.eq('status', status);

  const { data: bookings, error } = await req;
  if (error) throw new Error('Failed to fetch bookings');

  // In-memory search across purpose and hall name
  if (search) {
    const s = search.toLowerCase();
    return (bookings || []).filter(
      (b) =>
        b.purpose.toLowerCase().includes(s) ||
        b.hall?.name.toLowerCase().includes(s)
    );
  }

  return bookings || [];
};

/**
 * Cancels a future booking by ID for the authenticated user.
 */
const cancelBooking = async (bookingId, userId) => {
  // Fetch and validate ownership
  const { data: booking, error } = await supabase
    .from('bookings')
    .select('*')
    .eq('id', bookingId)
    .eq('user_id', userId)
    .maybeSingle();

  if (error || !booking) throw new Error('Booking not found');
  if (booking.status === 'cancelled') throw new Error('Booking is already cancelled');

  // Only future bookings can be cancelled
  const bookingDateTime = new Date(`${booking.date}T${booking.start_time}`);
  if (bookingDateTime <= new Date()) {
    throw new Error('Cannot cancel a booking that has already started or passed');
  }

  // Update status
  const { error: updateError } = await supabase
    .from('bookings')
    .update({ status: 'cancelled' })
    .eq('id', bookingId);

  if (updateError) throw new Error('Failed to cancel booking. Please try again.');

  // Fetch related data and send cancellation email (non-blocking)
  Promise.all([
    supabase.from('halls').select('*').eq('id', booking.hall_id).single(),
    supabase.from('users').select('*').eq('id', userId).single()
  ]).then(([{ data: hall }, { data: user }]) => {
    if (hall && user) {
      sendBookingCancellationEmail(user, booking, hall).catch((e) =>
        console.error('Cancellation email failed:', e.message)
      );
    }
  });

  return { message: 'Booking cancelled successfully' };
};

/**
 * Permanently deletes a booking from the database.
 * Users can delete any of their own bookings (past, cancelled, or future).
 */
const deleteBooking = async (bookingId, userId) => {
  // Verify ownership before deleting
  const { data: booking, error } = await supabase
    .from('bookings')
    .select('id, user_id')
    .eq('id', bookingId)
    .eq('user_id', userId)
    .maybeSingle();

  if (error || !booking) throw new Error('Booking not found');

  const { error: deleteError } = await supabase
    .from('bookings')
    .delete()
    .eq('id', bookingId);

  if (deleteError) throw new Error('Failed to delete booking. Please try again.');

  return { message: 'Booking deleted successfully' };
};

/**
 * Returns ALL bookings across all staff (for shared dashboard view).
 * Used by the real-time dashboard so every staff sees the full picture.
 */
const getAllBookings = async (query = {}) => {
  const { sort_by = 'date', sort_order = 'asc' } = query;

  const { data: bookings, error } = await supabase
    .from('bookings')
    .select('*, hall:halls(id, name, floor, location), user:users(id, first_name, last_name, department)')
    .order(sort_by, { ascending: sort_order === 'asc' })
    .order('start_time', { ascending: true });

  if (error) throw new Error('Failed to fetch bookings');
  return bookings || [];
};

module.exports = { createBooking, getUserBookings, getAllBookings, cancelBooking, deleteBooking };
