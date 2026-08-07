import React from 'react';
import { Link } from 'react-router-dom';
import Badge from './ui/Badge';
import { formatDate, formatTimeRange, getBookingTimeLabel } from '../utils/formatters';
import { useAuth } from '../context/AuthContext';

/**
 * Booking card for dashboard/list views.
 *
 * @param {object}   booking
 * @param {function} onCancel  - called with bookingId (only shown for future confirmed bookings)
 * @param {function} onDelete  - called with bookingId (shown for all bookings)
 * @param {boolean}  compact   - compact list item layout (dashboard)
 */
const BookingCard = ({ booking, onCancel, onDelete, compact = false }) => {
  const { id, hall, purpose, date, start_time, end_time, status, participants, user_id } = booking;
  const { user } = useAuth();
  const timeLabel = getBookingTimeLabel(date, start_time, end_time);

  // Badge variant
  const badgeVariant =
    status === 'cancelled' ? 'cancelled'
    : timeLabel === 'past'    ? 'past'
    : timeLabel === 'ongoing' ? 'ongoing'
    : 'upcoming';

  const badgeLabel =
    status === 'cancelled' ? 'Cancelled'
    : timeLabel === 'past'    ? 'Past'
    : timeLabel === 'ongoing' ? 'Ongoing'
    : 'Upcoming';

  // Only the booking owner can cancel or delete
  const isOwner = user && (user.id === user_id || user.id === booking.user?.id);

  // Only future confirmed bookings by the owner can be soft-cancelled
  const canCancel = isOwner && status === 'confirmed' && timeLabel === 'upcoming';
  // Only the owner can permanently delete their booking
  const canDelete = isOwner && !!onDelete;

  // ── Compact mode (dashboard) ──────────────────────────────────────────────
  if (compact) {
    return (
      <div className="flex items-center gap-3 py-3 border-b border-gray-50 last:border-0">
        <div className={`w-2 h-2 rounded-full flex-shrink-0 ${
          status === 'cancelled' ? 'bg-red-400'
          : timeLabel === 'ongoing' ? 'bg-orange-400'
          : timeLabel === 'past'   ? 'bg-gray-300'
          : 'bg-blue-500'
        }`} />
        <div className="flex-1 min-w-0">
          <p className="text-sm font-medium text-gray-900 truncate">{hall?.name}</p>
          <p className="text-xs text-gray-500">{formatDate(date)} · {formatTimeRange(start_time, end_time)}</p>
        </div>
        <Badge variant={badgeVariant}>{badgeLabel}</Badge>
      </div>
    );
  }

  // ── Full card mode (My Bookings page) ────────────────────────────────────
  return (
    <div className="card p-5 hover:shadow-md transition-shadow">
      {/* Header */}
      <div className="flex items-start justify-between gap-3 mb-4">
        <div>
          <p className="font-semibold text-gray-900 text-base">{hall?.name || 'Hall'}</p>
          <p className="text-xs text-gray-500 mt-0.5">{hall?.floor} — {hall?.location}</p>
        </div>
        <Badge variant={badgeVariant}>{badgeLabel}</Badge>
      </div>

      {/* Details grid */}
      <div className="grid grid-cols-2 gap-x-6 gap-y-3 text-sm text-gray-700 mb-4">
        <div>
          <span className="block text-xs text-gray-400 mb-0.5">Date</span>
          {formatDate(date)}
        </div>
        <div>
          <span className="block text-xs text-gray-400 mb-0.5">Time</span>
          {formatTimeRange(start_time, end_time)}
        </div>
        <div>
          <span className="block text-xs text-gray-400 mb-0.5">Purpose</span>
          <span className="truncate block">{purpose}</span>
        </div>
        <div>
          <span className="block text-xs text-gray-400 mb-0.5">Participants</span>
          {participants}
        </div>
      </div>

      {/* Action buttons */}
      {(canCancel || canDelete) && (
        <div className="pt-3 border-t border-gray-100 flex items-center justify-end gap-2">
          {/* Cancel — only for upcoming confirmed bookings */}
          {canCancel && onCancel && (
            <button
              onClick={() => onCancel(id)}
              className="btn btn-sm text-xs border border-orange-300 text-orange-600 bg-orange-50 hover:bg-orange-100 transition-colors rounded-lg px-3 py-1.5 font-medium"
            >
              Cancel Booking
            </button>
          )}

          {/* Delete — always shown, permanently removes */}
          {canDelete && (
            <button
              onClick={() => onDelete(id)}
              className="btn btn-sm btn-danger text-xs flex items-center gap-1.5"
            >
              <svg className="w-3.5 h-3.5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2}
                  d="M19 7l-.867 12.142A2 2 0 0116.138 21H7.862a2 2 0 01-1.995-1.858L5 7m5 4v6m4-6v6m1-10V4a1 1 0 00-1-1h-4a1 1 0 00-1 1v3M4 7h16" />
              </svg>
              Delete
            </button>
          )}
        </div>
      )}
    </div>
  );
};

export default BookingCard;
