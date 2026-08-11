/**
 * Click handler cho nút "Xuất PDF".
 *
 * - Nếu server trả về PDF (Content-Type: application/pdf): mở trong tab mới.
 * - Nếu server redirect về trang trước (kèm toast warning): điều hướng
 *   trang hiện tại về URL đó, để user thấy toast vàng.
 *
 * Hàm này đọc response Content-Type, không tải toàn bộ PDF xuống client.
 */
function xuatPdfVaMoTab(el, event) {
    if (event) event.preventDefault();
    if (!el || !el.href) return;

    // Tránh double-click
    if (el.dataset.busy === '1') return;
    el.dataset.busy = '1';

    fetch(el.href, { method: 'HEAD', redirect: 'follow', credentials: 'same-origin' })
        .then(function (resp) {
            var ct = resp.headers.get('Content-Type') || '';
            if (ct.indexOf('application/pdf') === 0) {
                window.open(el.href, '_blank');
            } else {
                // Server đã redirect — điều hướng tab hiện tại về trang đó.
                window.location.href = el.href;
            }
        })
        .catch(function () {
            // Lỗi mạng: fallback mở luôn trong cùng tab.
            window.location.href = el.href;
        })
        .finally(function () {
            el.dataset.busy = '0';
        });
}
