package com.tenco.blog.board;

import com.tenco.blog._core.errors.Exception403;
import com.tenco.blog._core.util.MyDateUtil;
import com.tenco.blog.user.User;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.CreationTimestamp;

import java.sql.Timestamp;

@Data
@Entity
@Table(name = "board_tb")
@NoArgsConstructor
@AllArgsConstructor // 전체 멤벼 번수를 넣을 수 있는 생성자.
@Builder
public class Board {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;
    private String title;
    private String content;

    @ColumnDefault("false")
    @Builder.Default // 이게 없으면 다른 파일에서 Build 객체 생성시 null 값이 셋팅 됨
    private Boolean premium = false;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id") // 외래키 컬럼명 표시 됨
    private User user;

    @CreationTimestamp
    private Timestamp createdAt;

    public String getTime() {
        return MyDateUtil.timestampFormat(createdAt);
    }

    public void update(BoardRequest.UpdateDTO updateDTO) {
        this.title = updateDTO.getTitle();
        this.content = updateDTO.getContent();
        // 유료 여부도 함께 수정
        this.premium = (updateDTO.getPremium() != null ? updateDTO.getPremium() : false);
    }

    public boolean isOwner(Integer sessionUserId) {
       if(!this.user.getId().equals(sessionUserId)) {
           throw new Exception403("본인이 작성한 게시글이 아닙니다");
       }
       return true;
    }
}
