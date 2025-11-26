package com.back.domain.member.member.controller

import com.back.domain.member.member.repository.MemberRepository
import com.back.standard.extentions.getOrThrow
import jakarta.servlet.http.Cookie
import org.hamcrest.Matchers
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders
import org.springframework.test.web.servlet.result.MockMvcResultHandlers
import org.springframework.test.web.servlet.result.MockMvcResultMatchers
import org.springframework.transaction.annotation.Transactional

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
@Transactional
class ApiV1AdmMemberControllerTest {

    @Autowired
    private lateinit var mvc: MockMvc

    @Autowired
    private lateinit var memberRepository: MemberRepository

    @Test
    @DisplayName("회원 다건 조회")
    @Throws(Exception::class)
    fun t1() {
        val actor = memberRepository.findByUsername("admin").getOrThrow()

        val resultActions = mvc
            .perform(
                MockMvcRequestBuilders.get("/api/v1/adm/members")
                    .cookie(Cookie("apiKey", actor.apiKey))
            )
            .andDo(MockMvcResultHandlers.print())

        resultActions
            .andExpect(MockMvcResultMatchers.handler().handlerType(ApiV1AdmMemberController::class.java))
            .andExpect(MockMvcResultMatchers.handler().methodName("getItems"))
            .andExpect(MockMvcResultMatchers.status().isOk())

        resultActions
            .andExpect(MockMvcResultMatchers.jsonPath("$.content.length()").value(5))
            .andExpect(MockMvcResultMatchers.jsonPath("$.content[*].id", Matchers.containsInRelativeOrder(8, 1)))
            .andExpect(MockMvcResultMatchers.jsonPath("$.content[0].id").value(1))
            .andExpect(MockMvcResultMatchers.jsonPath("$.content[0].createDate").exists())
            .andExpect(MockMvcResultMatchers.jsonPath("$.content[0].modifyDate").exists())
            .andExpect(MockMvcResultMatchers.jsonPath("$.content[0].nickname").value("시스템"))
            .andExpect(MockMvcResultMatchers.jsonPath("$.content[0].username").value("system"))
            .andExpect(MockMvcResultMatchers.jsonPath("$.content[0].isAdmin").value("false"))
    }

    @Test
    @DisplayName("회원 다건 조회, 권한이 없는 경우")
    @Throws(Exception::class)
    fun t2() {
        val actor = memberRepository.findByUsername("user1").getOrThrow()

        val resultActions = mvc
            .perform(
                MockMvcRequestBuilders.get("/api/v1/adm/members")
                    .cookie(Cookie("apiKey", actor.apiKey))
            )
            .andDo(MockMvcResultHandlers.print())

        resultActions
            .andExpect(MockMvcResultMatchers.status().isForbidden())
            .andExpect(MockMvcResultMatchers.jsonPath("$.resultCode").value("403-1"))
            .andExpect(MockMvcResultMatchers.jsonPath("$.msg").value("권한이 없습니다."))
    }
}
