package com.firstrep.identity;

import com.firstrep.common.TimedEntity;
import com.firstrep.project.Skill;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.util.HashSet;
import java.util.Set;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "student_profiles")
@Getter
@Setter
@NoArgsConstructor
public class StudentProfile extends TimedEntity {
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", unique = true)
    private UserAccount user;

    @Column(nullable = false, length = 160)
    private String displayName;

    @Column(length = 240)
    private String headline;

    @Column(length = 500)
    private String education;

    @Column(length = 500)
    private String targetRoles;

    @Column(columnDefinition = "text")
    private String bio;

    @Column(length = 500)
    private String portfolioUrl;

    @Column(length = 500)
    private String githubUrl;

    @Column(length = 500)
    private String cvObjectKey;

    @Column(length = 255)
    private String cvFilename;

    @Column(nullable = false)
    private boolean shareProfileInPackages;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "student_skills",
            joinColumns = @JoinColumn(name = "profile_id"),
            inverseJoinColumns = @JoinColumn(name = "skill_id"))
    private Set<Skill> skills = new HashSet<>();
}
