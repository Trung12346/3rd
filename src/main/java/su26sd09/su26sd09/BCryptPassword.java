package su26sd09.su26sd09;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

public class BCryptPassword {

    public static String bcrypt(String password){
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        return encoder.encode(password);
    }
    public static void main(String[] args) {
        System.out.println(bcrypt("1"));
    }
}
