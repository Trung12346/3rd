/**
 * MoneyFormat - dinh dang input tien te voi dau phan cach hang nghin (vd: 1,500,000)
 * trong khi van giu gia tri so nguyen "sach" (khong dau phay) khi submit form / doc
 * bang JS. Dung chung cho moi input tien o phia nhan vien (class="money-input").
 *
 * Cach dung:
 *   <input type="text" inputmode="numeric" class="money-input" name="giaMoiDem" ...>
 * JS tu dong:
 *   - Format lai gia tri hien co khi trang tai xong (DOMContentLoaded).
 *   - Format lai moi khi nguoi dung go (event "input"), giu nguyen vi tri con tro.
 *   - Truoc khi form cha submit, tu dong bo dau phay de gia tri gui len server
 *     van la so nguyen thuan (vi du "1,500,000" -> "1500000").
 * Doc gia tri tu JS (thay vi Number(el.value)/parseFloat(el.value)):
 *   MoneyFormat.parse(el)  hoac  MoneyFormat.parse('1,500,000')
 */
(function (window, document) {
    'use strict';

    /**
     * Chuan hoa 1 chuoi da format/tho thanh chuoi so nguyen thuan (VND, khong
     * co phan thap phan). QUAN TRONG: khong the chi xoa moi ky tu khong phai
     * chu so (regex /[^\d]/g), vi gia tri BigDecimal tu server (vd tu
     * th:field voi cot decimal(x,2)) co the render ra dang "1200000.00".
     * Neu xoa thang dau cham/phay thap phan thi "1200000.00" -> "120000000",
     * tuc bi nhan nham len 100 lan. Ham nay phat hien dau phan cach thap phan
     * (dau cham/phay cuoi cung, theo sau boi dung 1-2 chu so) va bo phan
     * thap phan do di truoc khi ghep cac nhom hang nghin lai.
     */
    function digitsOnly(value) {
        var s = (value || '').toString().trim();
        if (s === '') return '';

        var lastDot = s.lastIndexOf('.');
        var lastComma = s.lastIndexOf(',');
        var sepIndex = Math.max(lastDot, lastComma);

        if (sepIndex !== -1) {
            var fracPart = s.substring(sepIndex + 1);
            // Chi coi day la dau phan cach thap phan neu phan sau no la
            // 1-2 chu so thuan tuy (vd ".00", ",5") - con lai (vd dau phan
            // cach hang nghin ",000") thi giu nguyen de gop vao phan nguyen.
            if (/^\d{1,2}$/.test(fracPart)) {
                s = s.substring(0, sepIndex);
            }
        }

        return s.replace(/[^\d]/g, '');
    }

    /** Tra ve so nguyen (khong dau phay) tu 1 input hoac 1 chuoi da format. */
    function parse(elOrValue) {
        var raw = digitsOnly(typeof elOrValue === 'string' ? elOrValue : (elOrValue ? elOrValue.value : ''));
        return raw === '' ? 0 : parseInt(raw, 10);
    }

    /** Dinh dang lai input theo dau phan cach hang nghin, giu vi tri con tro hop ly. */
    function format(el) {
        var raw = digitsOnly(el.value);
        var caretFromEnd = el.value.length - (el.selectionEnd == null ? el.value.length : el.selectionEnd);
        el.value = raw === '' ? '' : Number(raw).toLocaleString('en-US');
        var newPos = el.value.length - caretFromEnd;
        if (el === document.activeElement && typeof el.setSelectionRange === 'function') {
            try { el.setSelectionRange(newPos, newPos); } catch (e) { /* input khong ho tro (vd type=number) - bo qua */ }
        }
    }

    var boundForms = new WeakSet();

    /** Truoc khi form submit, chuyen moi .money-input trong form ve so nguyen thuan. */
    function bindFormStrip(form) {
        if (!form || boundForms.has(form)) return;
        boundForms.add(form);
        form.addEventListener('submit', function () {
            form.querySelectorAll('input.money-input').forEach(function (el) {
                el.value = parse(el);
            });
        });
    }

    /** Gan dinh dang tu dong cho moi .money-input trong pham vi root (mac dinh: ca trang). */
    function init(root) {
        (root || document).querySelectorAll('input.money-input').forEach(function (el) {
            if (el.value) format(el);
            el.addEventListener('input', function () { format(el); });
            bindFormStrip(el.closest('form'));
        });
    }

    window.MoneyFormat = { format: format, parse: parse, init: init };
    document.addEventListener('DOMContentLoaded', function () { init(document); });
})(window, document);
