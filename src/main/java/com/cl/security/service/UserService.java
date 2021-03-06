package com.cl.security.service;

import com.cl.security.common.util.JwtTokenUtil;
import com.cl.security.repository.UserRepository;
import com.cl.security.entity.Permission;
import com.cl.security.entity.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Predicate;
import javax.persistence.criteria.Root;
import javax.servlet.http.HttpServletRequest;
import java.util.*;

/**
*
* @author chenlong
* @date 2020/12/8
*/
@Service
public class UserService{
    private final static Logger LOGGER = LoggerFactory.getLogger(UserService.class);
    @Autowired
    private UserDetailsService userDetailsService;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Value("${jwt.tokenHead}")
    private String tokenHead;
    @Value("${jwt.tokenHeader}")
    private String tokenHeader;
    @Autowired
    private UserRepository userRepository;

    public User getUserByUsername(String username) {
        Specification<User> specification = new Specification<User>() {
            @Override
            public Predicate toPredicate(Root<User> root, CriteriaQuery<?> criteriaQuery, CriteriaBuilder criteriaBuilder) {
                return criteriaBuilder.equal(root.get("username"), username);
            }
        };
        Optional<User> user = userRepository.findOne(specification);
        return user.orElse(null);

    }
    public String login(String username, String password) {
        String token = null;
        try {
            UserDetails userDetails = userDetailsService.loadUserByUsername(username);
            if (!passwordEncoder.matches(password,userDetails.getPassword())){
                throw new BadCredentialsException("密码不正确");
            }
            UsernamePasswordAuthenticationToken authenticationToken = new UsernamePasswordAuthenticationToken(userDetails,null, userDetails.getAuthorities());
            SecurityContextHolder.getContext().setAuthentication(authenticationToken);
            token = JwtTokenUtil.generateToken(userDetails);
        } catch (AuthenticationException e) {
            LOGGER.warn("登录异常：{}" + e.getMessage());
        }
        return token;
    }

    public User insert(User userParam) {
        User user = new User();
        BeanUtils.copyProperties(userParam, user);
        Specification<User> specification =
                (Specification<User>) (root, criteriaQuery, criteriaBuilder) ->
                        criteriaBuilder.equal(root.get("username"), userParam.getUsername());

        if (!userRepository.findAll(specification).isEmpty()) {
            return null;
        }
        String encodePassword = passwordEncoder.encode(user.getPassword());
        user.setPassword(encodePassword);

        return userRepository.save(user);
    }
    public Set<Permission> listPermission(Long id) {
        Optional<User> userOptional = userRepository.findById(id);
        Set<Permission> permissions = new HashSet<>();
        userOptional.ifPresent(user -> user.getRoles().forEach(role ->
                permissions.addAll(role.getPermissions())
        ));
        return permissions;
    }

    public Long getUserIdFromRequest(HttpServletRequest httpServletRequest)  {
        // 获取http请求头中的token
        String authHeader = httpServletRequest.getHeader(this.tokenHeader);
        if (authHeader != null && authHeader.startsWith(tokenHead)) {
            String authToken = authHeader.substring(this.tokenHead.length());
            String username = JwtTokenUtil.getUserNameFromToken(authToken);
            Specification<User> specification = (Specification<User>) (root, criteriaQuery, criteriaBuilder) -> criteriaBuilder.equal(root.get("username"), username);
            List<User> users = userRepository.findAll(specification);
            if (users.size() > 0) {
                return users.get(0).getId();
            }
        }
        return null;
    }

    public User getUserByToken(String token) {
        String username = JwtTokenUtil.getUserNameFromToken(token);
        return getUserByUsername(username);
    }

}
