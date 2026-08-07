import React, { useEffect, useState } from 'react';
import { useNavigate, useSearchParams, Link } from 'react-router-dom';
import DashboardLayout from '../layouts/DashboardLayout';
import Input from '../components/ui/Input';
import Button from '../components/ui/Button';
import Spinner from '../components/ui/Spinner';
import { hallsAPI, bookingsAPI } from '../services/api';
import { today } from '../utils/formatters';

// ── Time slot helpers ──────────────────────────────────────────────────────────
// Booking hours: 9:00 AM – 10:00 PM  (09:00 – 22:00)
const START_HOUR = 9;   // 9 AM
const END_HOUR   = 22;  // 10 PM  (last END slot = 22:00)

/** Convert "HH:MM" to a human-readable "H:MM AM/PM" label */
const toAmPm = (hhmm) => {
  const [h, m] = hhmm.split(':').map(Number);
  const period = h >= 12 ? 'PM' : 'AM';
  const hour   = h % 12 || 12;
  return `${hour}:${String(m).padStart(2, '0')} ${period}`;
};

/** Build a list of "HH:MM" strings in 30-min steps within [startHour, endHour] */
const buildSlots = (startHour, endHour) => {
  const slots = [];
  for (let h = startHour; h <= endHour; h++) {
    for (let m of [0, 30]) {
      if (h === endHour && m > 0) break; // e.g. stop at 22:00 sharp
      slots.push(`${String(h).padStart(2, '0')}:${String(m).padStart(2, '0')}`);
    }
  }
  return slots;
};

const ALL_SLOTS = buildSlots(START_HOUR, END_HOUR);

/**
 * Dropdown that shows time slots in AM/PM format.
 * `name`, `value`, `onChange`, `label`, `error` work like Input props.
 */
const TimeSelect = ({ name, value, onChange, label, error, slotFilter }) => {
  const slots = slotFilter ? ALL_SLOTS.filter(slotFilter) : ALL_SLOTS;
  return (
    <div>
      {label && (
        <label className="label">
          {label} <span className="text-red-500">*</span>
        </label>
      )}
      <select
        name={name}
        value={value}
        onChange={onChange}
        className={`input-field ${error ? 'input-error' : ''}`}
      >
        <option value="">Select time…</option>
        {slots.map((s) => (
          <option key={s} value={s}>{toAmPm(s)}</option>
        ))}
      </select>
      {error && <p className="mt-1.5 text-xs text-red-600">{error}</p>}
    </div>
  );
};

const BookHall = () => {
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const preselectedHallId = searchParams.get('hall_id') || '';

  const [halls, setHalls]     = useState([]);
  const [loading, setLoading] = useState(true);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError]     = useState('');
  const [success, setSuccess] = useState(false);

  const [form, setForm] = useState({
    hall_id: preselectedHallId,
    purpose: '',
    date: today(),
    start_time: '09:00',
    end_time: '10:00',
    participants: '',
    requirements: ''
  });
  const [errors, setErrors] = useState({});

  // Availability feedback
  const [avail, setAvail]         = useState(null);
  const [checkingAvail, setCheckingAvail] = useState(false);

  useEffect(() => {
    hallsAPI.getAll()
      .then(({ data }) => setHalls(data.halls || []))
      .finally(() => setLoading(false));
  }, []);

  // Check availability when hall/date/time changes
  useEffect(() => {
    if (!form.hall_id || !form.date || !form.start_time || !form.end_time) {
      setAvail(null);
      return;
    }
    if (form.start_time >= form.end_time) { setAvail(null); return; }

    const timer = setTimeout(async () => {
      setCheckingAvail(true);
      try {
        const { data } = await hallsAPI.getAvailability({
          hall_id: form.hall_id,
          date: form.date,
          start_time: form.start_time,
          end_time: form.end_time
        });
        setAvail(data);
      } catch {
        setAvail(null);
      } finally {
        setCheckingAvail(false);
      }
    }, 600); // debounce

    return () => clearTimeout(timer);
  }, [form.hall_id, form.date, form.start_time, form.end_time]);

  const handleChange = (e) => {
    const { name, value } = e.target;
    setForm((f) => ({ ...f, [name]: value }));
    setErrors((prev) => ({ ...prev, [name]: '' }));
    setError('');
  };

  const validate = () => {
    const errs = {};
    if (!form.hall_id)                         errs.hall_id = 'Please select a hall';
    if (!form.purpose || form.purpose.trim().length < 5)
                                               errs.purpose = 'At least 5 characters';
    if (!form.date)                            errs.date = 'Date is required';
    if (!form.start_time)                      errs.start_time = 'Required';
    if (!form.end_time)                        errs.end_time = 'Required';
    if (form.start_time && form.end_time && form.start_time >= form.end_time)
                                               errs.end_time = 'Must be after start time';
    // Enforce 9 AM – 10 PM window
    if (form.start_time && (form.start_time < '09:00' || form.start_time > '22:00'))
                                               errs.start_time = 'Must be between 9:00 AM and 10:00 PM';
    if (form.end_time && (form.end_time < '09:00' || form.end_time > '22:00'))
                                               errs.end_time = 'Must be between 9:00 AM and 10:00 PM';
    if (!form.participants || Number(form.participants) < 1)
                                               errs.participants = 'At least 1 participant';
    return errs;
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    const errs = validate();
    if (Object.keys(errs).length) { setErrors(errs); return; }

    setSubmitting(true);
    setError('');
    try {
      await bookingsAPI.create(form);
      setSuccess(true);
      setTimeout(() => navigate('/bookings'), 2000);
    } catch (err) {
      setError(err.response?.data?.error || 'Failed to create booking. Please try again.');
    } finally {
      setSubmitting(false);
    }
  };

  if (loading) {
    return (
      <DashboardLayout>
        <div className="flex items-center justify-center h-64"><Spinner size="lg" /></div>
      </DashboardLayout>
    );
  }

  if (success) {
    return (
      <DashboardLayout>
        <div className="max-w-lg mx-auto mt-16 text-center card p-10">
          <div className="w-16 h-16 bg-green-100 rounded-full flex items-center justify-center mx-auto mb-4">
            <svg className="w-8 h-8 text-green-600" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M5 13l4 4L19 7" />
            </svg>
          </div>
          <h2 className="text-xl font-semibold text-gray-900 mb-2">Booking Confirmed!</h2>
          <p className="text-gray-500 text-sm">
            Your hall has been booked. A confirmation email has been sent to your college and personal email.
          </p>
          <p className="text-xs text-gray-400 mt-3">Redirecting to your bookings…</p>
        </div>
      </DashboardLayout>
    );
  }

  return (
    <DashboardLayout>
      <div className="max-w-2xl mx-auto space-y-5">
        {/* Breadcrumb */}
        <nav className="flex items-center gap-2 text-sm text-gray-500">
          <Link to="/halls" className="hover:text-blue-600">Halls</Link>
          <span>/</span>
          <span className="text-gray-900 font-medium">Book a Hall</span>
        </nav>

        <h1 className="page-title">Book a Hall</h1>

        <form onSubmit={handleSubmit} className="space-y-5" noValidate>
          {error && <div className="alert-error">{error}</div>}

          {/* Hall selection */}
          <div className="card p-5">
            <h2 className="font-semibold text-gray-900 mb-4">Select Hall & Time</h2>

            <div className="space-y-4">
              <div>
                <label className="label">Hall <span className="text-red-500">*</span></label>
                <select
                  name="hall_id"
                  value={form.hall_id}
                  onChange={handleChange}
                  className={`input-field ${errors.hall_id ? 'input-error' : ''}`}
                >
                  <option value="">Choose a hall…</option>
                  {halls.map((h) => (
                    <option key={h.id} value={h.id}>
                      {h.name} — {h.floor}
                    </option>
                  ))}
                </select>
                {errors.hall_id && <p className="mt-1.5 text-xs text-red-600">{errors.hall_id}</p>}
              </div>

              <Input
                label="Date" name="date" type="date" required
                value={form.date} onChange={handleChange}
                error={errors.date} min={today()}
              />

              <div className="grid grid-cols-2 gap-3">
                {/* Start time — 9:00 AM to 9:30 PM (end must come after) */}
                <TimeSelect
                  label="Start Time" name="start_time"
                  value={form.start_time} onChange={handleChange}
                  error={errors.start_time}
                  slotFilter={(s) => s < '22:00'}
                />
                {/* End time — only slots strictly after the chosen start */}
                <TimeSelect
                  label="End Time" name="end_time"
                  value={form.end_time} onChange={handleChange}
                  error={errors.end_time}
                  slotFilter={(s) => s > (form.start_time || '09:00')}
                />
              </div>

              {/* Live availability indicator */}
              {form.hall_id && form.date && form.start_time && form.end_time && form.start_time < form.end_time && (
                <div className={`flex items-center gap-2 text-sm px-3 py-2.5 rounded-lg border ${
                  checkingAvail ? 'bg-gray-50 border-gray-200 text-gray-500' :
                  avail?.available === true  ? 'bg-green-50 border-green-200 text-green-700' :
                  avail?.available === false ? 'bg-red-50 border-red-200 text-red-700' :
                  'bg-gray-50 border-gray-200 text-gray-500'
                }`}>
                  {checkingAvail ? (
                    <><Spinner size="sm" />Checking availability…</>
                  ) : avail?.available === true ? (
                    <>✓ This time slot is available</>
                  ) : avail?.available === false ? (
                    <>✗ This slot conflicts with an existing booking ({avail.conflict?.start_time}–{avail.conflict?.end_time})</>
                  ) : null}
                </div>
              )}
            </div>
          </div>

          {/* Booking details */}
          <div className="card p-5">
            <h2 className="font-semibold text-gray-900 mb-4">Booking Details</h2>
            <div className="space-y-4">
              <Input
                label="Purpose" name="purpose" required
                value={form.purpose} onChange={handleChange}
                error={errors.purpose}
                placeholder="e.g., Faculty Meeting, Workshop, Guest Lecture"
              />
              <Input
                label="Expected Participants" name="participants" type="number" required
                value={form.participants} onChange={handleChange}
                error={errors.participants}
                min={1} placeholder="e.g., 25"
              />
              <div>
                <label className="label">Special Requirements <span className="text-gray-400 font-normal">(optional)</span></label>
                <textarea
                  name="requirements"
                  value={form.requirements}
                  onChange={handleChange}
                  rows={3}
                  className="input-field resize-none"
                  placeholder="e.g., Projector needed, microphone setup, etc."
                />
              </div>
            </div>
          </div>

          <div className="flex gap-3">
            <Button
              type="button"
              variant="secondary"
              onClick={() => navigate(-1)}
              className="flex-1"
            >
              Cancel
            </Button>
            <Button
              type="submit"
              variant="primary"
              loading={submitting}
              className="flex-1"
            >
              Confirm Booking
            </Button>
          </div>
        </form>
      </div>
    </DashboardLayout>
  );
};

export default BookHall;
