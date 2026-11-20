package com.firstrep.access;

import com.firstrep.common.AppException;
import com.firstrep.common.CurrentUser;
import com.firstrep.common.Kinds.CompanyStatus;
import com.firstrep.common.Kinds.MembershipStatus;
import com.firstrep.company.Company;
import com.firstrep.company.CompanyMembership;
import com.firstrep.company.CompanyMembershipRepository;
import com.firstrep.company.CompanyRepository;
import org.springframework.stereotype.Component;

@Component
public class AccessGuard {
    private final CurrentUser currentUser;
    private final CompanyRepository companies;
    private final CompanyMembershipRepository memberships;

    public AccessGuard(CurrentUser currentUser, CompanyRepository companies, CompanyMembershipRepository memberships) {
        this.currentUser = currentUser;
        this.companies = companies;
        this.memberships = memberships;
    }

    public void requirePlatform(String permission) {
        if (!currentUser.hasAuthority(permission)) {
            throw AppException.forbidden("You do not have access to that.");
        }
    }

    public CompanyMembership requireMember(String companyPublicId) {
        var user = currentUser.require();
        Company company = companies.findByPublicId(companyPublicId).orElseThrow(() -> AppException.notFound("Company not found."));
        CompanyMembership membership = memberships.findByCompanyIdAndUserId(company.getId(), user.getId())
                .filter(item -> item.getStatus() == com.firstrep.common.Kinds.MembershipStatus.ACTIVE)
                .orElseThrow(() -> AppException.notFound("Company not found."));
        if (memberships.permissionCodes(user.getId(), company.getId()).isEmpty()) {
            throw AppException.forbidden("You do not have access to that.");
        }
        return membership;
    }

    public CompanyMembership requireCompany(String companyPublicId, String permission) {
        var user = currentUser.require();
        Company company = companies.findByPublicId(companyPublicId).orElseThrow(() -> AppException.notFound("Company not found."));
        if (company.getStatus() != CompanyStatus.ACTIVE) {
            throw AppException.notFound("Company not found.");
        }
        CompanyMembership membership = memberships.findByCompanyIdAndUserId(company.getId(), user.getId())
                .filter(item -> item.getStatus() == MembershipStatus.ACTIVE)
                .orElseThrow(() -> AppException.notFound("Company not found."));
        if (!memberships.permissionCodes(user.getId(), company.getId()).contains(permission)) {
            throw AppException.forbidden("You do not have access to that.");
        }
        return membership;
    }
}
