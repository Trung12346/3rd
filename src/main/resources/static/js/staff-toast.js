/**
 * staff-toast.js
 * Small top-right notification popups for staff ("nhan-vien") pages.
 * Include once (already wired into fragments/headers/staff-header.html)
 * and call window.staffToast(message, type) from anywhere.
 *
 *   staffToast('Đã lưu thành công!');            // type defaults to 'success'
 *   staffToast('Không thể lưu.', 'error');
 *   staffToast('Đang xử lý...', 'info');
 */
(function () {
    if (window.staffToast) return; // avoid double-init if included twice

    var CONTAINER_ID = 'staff-toast-container';

    function ensureStyles() {
        if (document.getElementById('staff-toast-styles')) return;
        var style = document.createElement('style');
        style.id = 'staff-toast-styles';
        style.textContent = [
            '#' + CONTAINER_ID + '{position:fixed;top:16px;right:16px;z-index:99999;',
            'display:flex;flex-direction:column;gap:10px;pointer-events:none;',
            'max-width:340px;}',
            '.staff-toast{pointer-events:auto;display:flex;align-items:flex-start;gap:10px;',
            'padding:12px 14px;border-radius:10px;box-shadow:0 6px 20px rgba(0,0,0,.15);',
            'background:#fff;border-left:4px solid #10b981;font-size:14px;line-height:1.4;',
            'color:#1f2937;opacity:0;transform:translateX(24px);',
            'transition:opacity .25s ease,transform .25s ease;font-family:inherit;}',
            '.staff-toast.show{opacity:1;transform:translateX(0);}',
            '.staff-toast.hide{opacity:0;transform:translateX(24px);}',
            '.staff-toast-icon{flex:0 0 auto;width:20px;height:20px;border-radius:50%;',
            'display:flex;align-items:center;justify-content:center;color:#fff;font-size:13px;',
            'font-weight:700;margin-top:1px;}',
            '.staff-toast-success .staff-toast-icon{background:#10b981;}',
            '.staff-toast-success{border-left-color:#10b981;}',
            '.staff-toast-error .staff-toast-icon{background:#ef4444;}',
            '.staff-toast-error{border-left-color:#ef4444;}',
            '.staff-toast-info .staff-toast-icon{background:#3b82f6;}',
            '.staff-toast-info{border-left-color:#3b82f6;}',
            '.staff-toast-msg{flex:1 1 auto;word-break:break-word;}',
            '.staff-toast-close{flex:0 0 auto;background:none;border:none;cursor:pointer;',
            'color:#9ca3af;font-size:16px;line-height:1;padding:0;margin-left:4px;}',
            '.staff-toast-close:hover{color:#4b5563;}',
            '@media (max-width:480px){#' + CONTAINER_ID + '{left:12px;right:12px;max-width:none;}}'
        ].join('');
        document.head.appendChild(style);
    }

    function ensureContainer() {
        var el = document.getElementById(CONTAINER_ID);
        if (!el) {
            el = document.createElement('div');
            el.id = CONTAINER_ID;
            document.body.appendChild(el);
        }
        return el;
    }

    var ICONS = { success: '✓', error: '✕', info: 'i' };
    var QUEUE_KEY = 'staffToastQueued';

    window.staffToast = function (message, type, durationMs) {
        if (!message) return;
        type = (type === 'error' || type === 'info') ? type : 'success';
        durationMs = typeof durationMs === 'number' ? durationMs : 3200;

        ensureStyles();
        var container = ensureContainer();

        var toast = document.createElement('div');
        toast.className = 'staff-toast staff-toast-' + type;
        toast.setAttribute('role', 'status');
        toast.innerHTML =
            '<span class="staff-toast-icon">' + ICONS[type] + '</span>' +
            '<span class="staff-toast-msg"></span>' +
            '<button type="button" class="staff-toast-close" aria-label="Đóng">&times;</button>';
        toast.querySelector('.staff-toast-msg').textContent = message;

        container.appendChild(toast);
        requestAnimationFrame(function () { toast.classList.add('show'); });

        var timer = setTimeout(remove, durationMs);

        function remove() {
            clearTimeout(timer);
            toast.classList.remove('show');
            toast.classList.add('hide');
            setTimeout(function () { toast.remove(); }, 250);
        }

        toast.querySelector('.staff-toast-close').addEventListener('click', remove);
        return remove;
    };

    /**
     * Queue a toast to survive a full page reload/navigation (e.g. before
     * calling window.location.reload() or after a form POST redirect).
     * The queued toast is shown once, automatically, on the next page load.
     */
    window.staffToastQueue = function (message, type) {
        if (!message) return;
        try {
            sessionStorage.setItem(QUEUE_KEY, JSON.stringify({ message: message, type: type || 'success' }));
        } catch (e) { /* sessionStorage unavailable - ignore, best effort */ }
    };

    document.addEventListener('DOMContentLoaded', function () {
        try {
            var raw = sessionStorage.getItem(QUEUE_KEY);
            if (!raw) return;
            sessionStorage.removeItem(QUEUE_KEY);
            var queued = JSON.parse(raw);
            if (queued && queued.message) window.staffToast(queued.message, queued.type);
        } catch (e) { /* ignore malformed/unavailable storage */ }
    });
})();
