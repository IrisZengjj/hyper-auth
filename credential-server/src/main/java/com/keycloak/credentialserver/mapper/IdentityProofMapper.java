package com.keycloak.credentialserver.mapper; // 包名与路径完全一致

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.springframework.stereotype.Repository;
import com.keycloak.credentialserver.entity.IdentityProof; // 导入当前包下的entity

/**
 * 身份凭证Mapper接口（数据库访问层）
 */
@Repository
public interface IdentityProofMapper extends BaseMapper<IdentityProof> {

    /**
     * 根据最终凭证哈希查询有效记录
     * @param finalCredentialHash 认证器传入的最终凭证哈希
     * @return 身份凭证实体（含phoneNumberHash）
     */
    IdentityProof selectByFinalCredentialHash(@Param("finalCredentialHash") String finalCredentialHash);
}

