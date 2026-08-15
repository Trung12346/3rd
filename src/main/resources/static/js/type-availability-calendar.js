/**
 * TypeAvailabilityCalendar
 * =========================
 * Calendar chon khoang ngay (nhan phong / tra phong) danh rieng cho trang
 * chi tiet LOAI PHONG (/loai-phong/{id}). Cau truc va hanh vi chon ngay duoc
 * COPY va DIEU CHINH tu hero calendar cua index.html (xem
 * static/js/date-range-picker.js - file goc KHONG bi sua o day), voi 2 khac
 * biet chinh:
 *
 *   1) Chi hien thi 1 THANG tai 1 thoi diem (index.html dung duration 2
 *      thang), theo dung yeu cau cho calendar dat phong theo loai.
 *   2) Tu dong goi API GET /loai-phong/{id}/ngay-het-phong?thang=yyyy-MM moi
 *      khi thang dang xem thay doi (bam prev/next), va disable (khong cho
 *      bam) cac ngay ma loai phong dang xem da het sach phong hoat dong.
 *
 * File nay HOAN TOAN DOC LAP - khong extends/import DateRangePicker cua
 * index.html, de khong dung chung state va khong co nguy co anh huong nguoc
 * lai calendar cua trang chu.
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
    return !!a && !!b && a.toDateString() === b.toDateString();
  }

  function yearMonthKey(d) {
    return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}`;
  }

  function startOfDay(d) {
    return new Date(d.getFullYear(), d.getMonth(), d.getDate());
  }

  class TypeAvailabilityCalendar {
    /**
     * @param {Object} options
     * @param {string} options.calendarElId    id cua the div se chua FullCalendar
     * @param {number} options.loaiPhongId     id loai phong dang xem (bat buoc)
     * @param {Date}   [options.minDate]        ngay nho nhat duoc chon (mac dinh: hom nay)
     * @param {Function} [options.onChange]     (startDate, endDate) => {}
     */
    constructor(options) {
      options = options || {};
      this.calendarEl = document.getElementById(options.calendarElId);
      this.loaiPhongId = options.loaiPhongId;
      this.onChange = typeof options.onChange === 'function' ? options.onChange : function () {};
      this.minDate = startOfDay(options.minDate || new Date());

      this.startDate = null;
      this.endDate = null;

      // Cache disabled-ranges theo thang da tai ve, key = 'yyyy-MM'.
      // Gia tri: mang cac {start: Date, end: Date} (inclusive ca 2 dau).
      this._disabledByMonth = new Map();
      this._loadingMonths = new Set();

      this.calendar = null;
      this._init();
    }

    _init() {
      if (!this.calendarEl) {
        console.warn('TypeAvailabilityCalendar: khong tim thay phan tu #' + this.calendarEl);
        return;
      }
      if (typeof FullCalendar === 'undefined') {
        console.warn('TypeAvailabilityCalendar: thu vien FullCalendar chua duoc tai.');
        return;
      }
      if (!this.loaiPhongId) {
        console.warn('TypeAvailabilityCalendar: thieu loaiPhongId.');
      }

      this.calendar = new FullCalendar.Calendar(this.calendarEl, {
        initialView: 'dayGridMonth', // 1 thang / 1 lan xem (khac hero calendar 2-thang cua trang chu)
        locale: 'vi',
        headerToolbar: { left: 'prev', center: 'title', right: 'next' },
        height: 'auto',
        fixedWeekCount: false,
        validRange: { start: toISODate(this.minDate) },
        dateClick: (info) => this._handleDateClick(info.date),
        dayCellClassNames: (arg) => this._dayCellClassNames(arg.date),
        datesSet: (info) => this._onDatesSet(info)
      });

      this.calendar.render();
    }

    // ----- Vung ngay bi khoa (het phong) -----

    _isDisabled(date) {
      const ranges = this._disabledByMonth.get(yearMonthKey(date));
      // Neu thang nay CHUA tai xong du lieu, mac dinh coi la KHONG khoa
      // (fail-open): tranh chan nham khach trong luc dang cho API, viec
      // kiem tra that su (con phong hay khong) van duoc server thuc hien lai
      // khi submit o /loai-phong/dat-nhanh.
      if (!ranges) return false;
      return ranges.some((r) => date >= r.start && date <= r.end);
    }

    _coKhoangBiKhoaGiua(start, end) {
      const cursor = new Date(start.getFullYear(), start.getMonth(), start.getDate());
      const cuoi = new Date(end.getFullYear(), end.getMonth(), end.getDate());
      while (cursor < cuoi) {
        cursor.setDate(cursor.getDate() + 1);
        if (this._isDisabled(cursor)) return true;
      }
      return false;
    }

    // ----- Tuong tac chon ngay (copy logic tu hero calendar cua index.html) -----

    _handleDateClick(clickedRaw) {
      const clicked = startOfDay(clickedRaw);

      if (clicked < this.minDate) return;
      if (this._isDisabled(clicked)) return; // ngay het phong -> khong cho chon

      if (!this.startDate || (this.startDate && this.endDate)) {
        this.startDate = clicked;
        this.endDate = null;
      } else if (clicked <= this.startDate) {
        this.startDate = clicked;
        this.endDate = null;
      } else {
        // Khong cho chon ngay tra phong neu giua duong co it nhat 1 ngay da het phong,
        // vi nhu vay se khong co 1 phong duy nhat nao trong suot ca ky nghi.
        if (this._coKhoangBiKhoaGiua(this.startDate, clicked)) {
          return;
        }
        this.endDate = clicked;
      }

      if (this.calendar) this.calendar.render();
      this.onChange(this.startDate, this.endDate);
    }

    _dayCellClassNames(dateRaw) {
      const date = startOfDay(dateRaw);
      const classes = [];

      const isStart = this.startDate && sameDay(date, this.startDate);
      const isEnd = this.endDate && sameDay(date, this.endDate);
      const isMid = this.startDate && this.endDate && date > this.startDate && date < this.endDate;
      const isOnly = this.startDate && !this.endDate && sameDay(date, this.startDate);

      if (isStart) classes.push('drp-range-start');
      if (isEnd) classes.push('drp-range-end');
      if (isMid) classes.push('drp-range-mid');
      if (isOnly) classes.push('drp-range-only');
      if (this._isDisabled(date)) classes.push('drp-day-fully-booked');

      return classes;
    }

    // ----- Tai du lieu "het phong" theo thang dang hien thi -----

    _onDatesSet(info) {
      if (!this.loaiPhongId) return;

      // info.view.currentStart / currentEnd (currentEnd la exclusive) la
      // khoang ngay "thuc" cua thang dang xem (khong tinh cac ngay dem cua
      // thang truoc/sau hien o ria luoi) - thuoc tinh cong khai cua FullCalendar.
      const start = info.view.currentStart;
      const end = info.view.currentEnd;

      const thangCanTai = new Set();
      const cursor = new Date(start.getFullYear(), start.getMonth(), 1);
      while (cursor < end) {
        thangCanTai.add(yearMonthKey(cursor));
        cursor.setMonth(cursor.getMonth() + 1);
      }
      thangCanTai.forEach((key) => this._taiThangNeuChuaCo(key));
    }

    _taiThangNeuChuaCo(thangKey) {
      if (this._disabledByMonth.has(thangKey) || this._loadingMonths.has(thangKey)) return;
      this._loadingMonths.add(thangKey);

      const url = `/loai-phong/${this.loaiPhongId}/ngay-het-phong?thang=${encodeURIComponent(thangKey)}`;
      fetch(url, { headers: { Accept: 'application/json' } })
        .then((res) => (res.ok ? res.json() : []))
        .then((list) => {
          const ranges = (list || []).map((r) => ({
            start: new Date(r.tuNgay + 'T00:00:00'),
            end: new Date(r.denNgay + 'T00:00:00')
          }));
          this._disabledByMonth.set(thangKey, ranges);
          this._loadingMonths.delete(thangKey);
          if (this.calendar) this.calendar.render();
        })
        .catch((err) => {
          console.warn('TypeAvailabilityCalendar: khong tai duoc ngay het phong cho ' + thangKey, err);
          this._loadingMonths.delete(thangKey);
          // KHONG cache thang loi -> lan render/datesSet ke tiep se tu thu lai.
        });
    }

    // ----- Tien ich public -----

    reset() {
      this.startDate = null;
      this.endDate = null;
      if (this.calendar) this.calendar.render();
      this.onChange(null, null);
    }

    refresh() {
      if (this.calendar) requestAnimationFrame(() => this.calendar.updateSize());
    }

    destroy() {
      if (this.calendar) this.calendar.destroy();
    }
  }

  window.TypeAvailabilityCalendar = TypeAvailabilityCalendar;
})(window);
