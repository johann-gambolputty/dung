package org

import org.eclipse.jetty.server.Request
import org.eclipse.jetty.server.handler.AbstractHandler
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse
import javax.ws.rs.GET
import javax.ws.rs.Path
import javax.ws.rs.Produces
import javax.ws.rs.core.MediaType

class HelloHandler : AbstractHandler() {
    override fun doHandle(target: String?, baseRequest: Request?, request: HttpServletRequest?, response: HttpServletResponse?) {
        if (response == null || baseRequest == null) {
            return;
        }
        response.setContentType("text/html; charset=utf-8")
        response.setStatus(HttpServletResponse.SC_OK)
        val out = response.getWriter()
        out.println("<h1>Hello</h1>")
        baseRequest.setHandled(true)
    }
}

@Path("hello")
class HelloResource {
    @GET
    @Path("hello")
    @Produces(MediaType.TEXT_PLAIN)
    fun helloWorld(): String {
        return "Hello, world!"
    }
}