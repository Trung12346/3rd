package su26sd09.su26sd09.service;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Service;
import su26sd09.su26sd09.dto.PendingBookingDraft;

import java.util.HashMap;
import java.util.Map;

/**
 * Luu "ban nhap" dat phong (PendingBookingDraft) trong SESSION cua trinh
 * duyet, thay vi tao ban ghi dat_phong trong DB, cho toi khi khach bam
 * "Hoan tat dat phong".
 *
 * De KHONG phai doi kieu du lieu @PathVariable id (int) o khap noi
 * (template, controller khac, luong thanh toan/nhan vien da dung id kieu
 * int cho DatPhong that), ban nhap duoc gan 1 "pending id" la SO AM
 * (vd -1, -2, ...). Quy uoc:
 *   - id >= 0  -> DatPhong THAT, da ton tai trong DB (nhu truoc gio).
 *   - id <  0  -> ban nhap dang cho trong session, CHUA co dong nao trong DB.
 * Nho vay cac trang /phong/dat-phong/xac-nhan/{id} va
 * /phong/dat-phong/thong-tin-khach/{id} dung chung 1 URL pattern / template
 * cho ca 2 truong hop, controller chi re nhanh theo dau cua id.
 */
@Service
public class PendingBookingService {

    private static final String SESSION_DRAFTS = "PENDING_BOOKING_DRAFTS";
    private static final String SESSION_SEQ = "PENDING_BOOKING_SEQ";
    public static final String COOKIE_NAME = "GUEST_PENDING_BOOKING";
    private static final int COOKIE_MAX_AGE_SECONDS = 60 * 60 * 24;

    public void remember(HttpServletResponse response, int id) {
        response.addHeader(
                "Set-Cookie",
                COOKIE_NAME + "=" + id
                        + "; Path=/"
                        + "; Max-Age=" + COOKIE_MAX_AGE_SECONDS
                        + "; HttpOnly"
                        + "; SameSite=Lax"
        );
    }
    public Integer peekId(HttpServletRequest request) {
        if (request.getCookies() == null) {
            return null;
        }

        for (Cookie cookie : request.getCookies()) {
            if (COOKIE_NAME.equals(cookie.getName())) {
                try {
                    return Integer.parseInt(cookie.getValue());
                } catch (NumberFormatException e) {
                    return null;
                }
            }
        }

        return null;
    }
    public void consume(HttpServletResponse response) {

        Cookie cookie = new Cookie(COOKIE_NAME, "");
        cookie.setPath("/");
        cookie.setMaxAge(0);

        response.addCookie(cookie);
    }
    public PendingBookingDraft peek(HttpServletRequest request) {
        Integer id = peekId(request);

        if (id == null || id >= 0) {
            return null;
        }

        return get(request, id);
    }
    @SuppressWarnings("unchecked")
    private Map<Integer, PendingBookingDraft> drafts(HttpSession session) {
        Object o = session.getAttribute(SESSION_DRAFTS);
        if (o instanceof Map) {
            return (Map<Integer, PendingBookingDraft>) o;
        }
        Map<Integer, PendingBookingDraft> map = new HashMap<>();
        session.setAttribute(SESSION_DRAFTS, map);
        return map;
    }

    /** Tao ban nhap moi, tra ve pending id (so am) de dua vao URL. */
    public synchronized int create(HttpServletRequest request, PendingBookingDraft draft) {
        HttpSession session = request.getSession(true);
        Integer seq = (Integer) session.getAttribute(SESSION_SEQ);
        int next = (seq == null ? 0 : seq) - 1;
        session.setAttribute(SESSION_SEQ, next);
        drafts(session).put(next, draft);
        return next;
    }

    /** @return ban nhap ung voi pending id, hoac null neu id>=0 / khong ton tai / het han session. */
    public PendingBookingDraft get(HttpServletRequest request, int id) {
        if (id >= 0) {
            return null;
        }
        HttpSession session = request.getSession(false);
        if (session == null) {
            return null;
        }
        return drafts(session).get(id);
    }

    /** Ghi de ban nhap (vd sau khi khach chon xong dich vu bo sung o buoc xac-nhan). */
    public void update(HttpServletRequest request, int id, PendingBookingDraft draft) {
        if (id >= 0) {
            return;
        }
        HttpSession session = request.getSession(true);
        drafts(session).put(id, draft);
    }

    /** Xoa ban nhap - goi sau khi da tao DatPhong that (buoc Hoan tat dat phong) hoac khach huy. */
    public void remove(HttpServletRequest request, int id) {
        if (id >= 0) {
            return;
        }
        HttpSession session = request.getSession(false);
        if (session == null) {
            return;
        }
        drafts(session).remove(id);
    }

    public boolean isPending(int id) {
        return id < 0;
    }
}
