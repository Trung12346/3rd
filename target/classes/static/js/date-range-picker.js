/**
 * DateRangePicker - reusable check-in / check-out range picker built on FullCalendar.
 * Requires FullCalendar global bundle to be loaded on the page before this file.
 *
 * Usage:
 *   const picker = new DateRangePicker({
 *       calendarElId: 'someCalendarDiv',
 *       minDate: new Date(),
 *       onChange: (start, end) => { ... }
 *   });
 *   picker.reset();
 *   picker.setRange(startDate, endDate);
 */
(function (window) {
    'use strict';

    function toISODate(d) {
        const y = d.getFullYear();
        const m = String(d.getMonth() + 1).padStart(2, '0');
        const day = String(d.getDate()).padStart(2, '0');
        return `${y}-${m}-${day}`;
    }

    function sameDay(a, b) {
        return a && b && a.toDateString() === b.toDateString();
    }

    class DateRangePicker {
        /**
         * @param {Object} options
         * @param {string} options.calendarElId - id of the element FullCalendar mounts into.
         * @param {number} [options.scale=1] - visual scale factor (e.g. 0.6 = 60% of natural size).
         *   Implemented by wrapping the element and applying a CSS transform, then resizing the
         *   wrapper to the scaled dimensions so surrounding layout doesn't leave empty space.
         */
        constructor(options) {
            this.calendarEl = document.getElementById(options.calendarElId);
            this.onChange = options.onChange || function () {};
            this.minDate = options.minDate || new Date();
            this.locale = options.locale || 'vi';
            this.startDate = options.startDate || null;
            this.endDate = options.endDate || null;
            this.scale = options.scale && options.scale > 0 ? options.scale : 1;
            this._init();
        }

        _init() {
            if (!this.calendarEl || typeof FullCalendar === 'undefined') {
                console.warn('DateRangePicker: missing target element or FullCalendar library.');
                return;
            }

            if (this.scale !== 1) {
                this._setupScaleWrapper();
            }

            this.calendar = new FullCalendar.Calendar(this.calendarEl, {
                initialView: 'dayGridMonth',
                locale: this.locale,
                duration: { months: 2 },
                headerToolbar: { left: 'prev', center: 'title', right: 'next' },
                height: 'auto',
                fixedWeekCount: false,
                validRange: { start: toISODate(this.minDate) },
                dateClick: (info) => this._handleDateClick(info.date),
                dayCellClassNames: (arg) => this._dayCellClassNames(arg.date),
                dayCellDidMount: (arg) => this._decorateCell(arg),
                datesSet: () => this._syncScale()
            });

            this.calendar.render();
            this._syncScale();
        }

        _setupScaleWrapper() {
            const wrapper = document.createElement('div');
            wrapper.className = 'drp-scale-wrapper';
            this.calendarEl.parentNode.insertBefore(wrapper, this.calendarEl);
            wrapper.appendChild(this.calendarEl);
            this.wrapperEl = wrapper;
            this.calendarEl.style.transform = `scale(${this.scale})`;
        }

        _syncScale() {
            if (this.scale === 1 || !this.wrapperEl) return;
            // Let the layout settle at natural size, then shrink the wrapper box
            // to match the scaled visual size so nothing below/right is left blank.
            requestAnimationFrame(() => {
                const naturalWidth = this.calendarEl.offsetWidth;
                const naturalHeight = this.calendarEl.offsetHeight;
                // The popover (and therefore this element) may still be hidden
                // (display: none) the first time this runs, e.g. right after
                // construction, before the user has opened it. Measuring a
                // hidden element gives 0x0, which would squash the wrapper down
                // to nothing and only get fixed the next time something else
                // (like the Xóa button) happened to trigger a re-sync while the
                // popover was visible. Skip syncing until we get a real size,
                // and let refresh() (called when the popover opens) retry.
                if (naturalWidth === 0 || naturalHeight === 0) return;
                this.wrapperEl.style.width = `${naturalWidth * this.scale}px`;
                this.wrapperEl.style.height = `${naturalHeight * this.scale}px`;
            });
        }

        /**
         * Re-measure and re-apply the scaled wrapper size. Call this whenever
         * the calendar becomes visible (e.g. right after opening the popover
         * it lives in), since sizes can't be measured while hidden.
         *
         * FullCalendar itself also needs to be told to recompute its internal
         * layout in this situation: it was constructed/rendered while the
         * popover was still `display: none`, so it measured a 0-width
         * container and never laid itself out correctly. Calling
         * updateSize() here (not just the scale-wrapper sync, which is a
         * no-op when scale === 1) forces FullCalendar to re-measure now that
         * the popover is actually visible. Without this, the calendar only
         * ever looked right after something else (like the "Xóa" button)
         * triggered a full calendar.render().
         */
        refresh() {
            if (this.calendar) {
                requestAnimationFrame(() => this.calendar.updateSize());
            }
            this._syncScale();
        }

        _handleDateClick(clicked) {
            if (clicked < this.minDate && !sameDay(clicked, this.minDate)) return;

            if (!this.startDate || (this.startDate && this.endDate)) {
                this.startDate = clicked;
                this.endDate = null;
            } else if (clicked <= this.startDate) {
                this.startDate = clicked;
                this.endDate = null;
            } else {
                this.endDate = clicked;
            }

            this.calendar.render();
            this._syncScale();
            this.onChange(this.startDate, this.endDate);
        }

        _dayCellClassNames(date) {
            const classes = [];
            if (this.startDate && sameDay(date, this.startDate)) classes.push('drp-range-start');
            if (this.endDate && sameDay(date, this.endDate)) classes.push('drp-range-end');
            if (this.startDate && this.endDate && date > this.startDate && date < this.endDate) {
                classes.push('drp-range-mid');
            }
            if (this.startDate && !this.endDate && sameDay(date, this.startDate)) {
                classes.push('drp-range-only');
            }
            return classes;
        }

        _decorateCell(arg) {
            // no-op hook kept for future customization (e.g. price-per-night badges)
        }

        setRange(start, end) {
            this.startDate = start || null;
            this.endDate = end || null;
            if (this.calendar) {
                this.calendar.render();
                this._syncScale();
            }
        }

        reset() {
            this.setRange(null, null);
        }

        destroy() {
            if (this.calendar) this.calendar.destroy();
        }
    }

    window.DateRangePicker = DateRangePicker;
})(window);
