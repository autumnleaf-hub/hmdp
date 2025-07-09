package com.hmdp.interceptor;

import com.hmdp.dto.UserDTO;
import com.hmdp.utils.UserHolder;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;

/**
 * @author fzy
 * @version 1.0
 * 创建时间：2025-05-10 16:14
 * 用于拦截未登录的请求
 */
@Component
public class LoginInterceptor implements HandlerInterceptor {

    /**
     * 在Controller方法处理之前进行调用。
     * @param handler
     * @return 可以通过控制返回值来决定是否继续执行后续的拦截器或者处理器。
     *         true表示继续执行后续的拦截器或者处理器，false表示中断后续的拦截器或者处理器的执行。
     */
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        UserDTO user = UserHolder.getUser();
        if (user == null) {
            response.setStatus(401);
            return false;  // 拦截未登录请求
        }
        return true;
    }



    /**
     * 在Controller方法处理后，视图渲染之前执行。
     * 我们可以通过此方法对请求域中的模型和视图做进一步的修改。
     * 一般没用，因为没人还会在后端渲染视图，而是前端渲染。
     */
    @Override
    public void postHandle(HttpServletRequest request, HttpServletResponse response, Object handler, ModelAndView modelAndView) throws Exception {
    }

    /**
     * 在整个请求完成之后，即视图渲染之后调用，主要用于资源清理工作。
     * 无论preHandle返回true或false，或者Controller执行过程中是否抛出异常，afterCompletion都会执行（除非之前的拦截器preHandle返回false）。
     * @param handler
     * @param ex
     */
    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
    }
}
