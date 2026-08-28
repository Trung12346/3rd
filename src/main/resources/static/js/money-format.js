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

    function digitsOnly(value) {
        return (value || '').toString().replace(/[^\d]/g, '');
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
