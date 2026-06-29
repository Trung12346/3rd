package su26sd09.su26sd09.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "xac_thuc_token")
@Getter
@Setter
@NoArgsConstructor
public class VerificationToken {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ma_token")
    private Integer id;

    @Column(name = "token")
    private String token;

    @Column(name = "thoi_gian_het_han")
    private LocalDateTime expiry_date;

    @Column(name = "da_su_dung")
    private Boolean used = false;

    @ManyToOne
    @JoinColumn(name = "ma_nguoi_dung",referencedColumnName = "ma_nguoi_dung")
    @JsonIgnore
    private NguoiDung nguoiDung;

    public VerificationToken(String token ,NguoiDung nguoiDung){
    this.nguoiDung = nguoiDung;
    this.token = token;
    this.expiry_date = LocalDateTime.now().plusMinutes(11);
    }

    public boolean isExpired(){
        return LocalDateTime.now().isAfter(this.expiry_date);
    }
}
