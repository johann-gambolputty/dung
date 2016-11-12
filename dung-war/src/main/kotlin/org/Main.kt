package org

import org.eclipse.jetty.server.Server
import org.eclipse.jetty.servlet.ServletContextHandler
import org.eclipse.jetty.servlet.ServletHolder
import org.eclipse.jetty.webapp.WebAppContext
import org.glassfish.jersey.server.ResourceConfig
import org.glassfish.jersey.servlet.ServletContainer

fun main(args: Array<String>) {
    val server = Server(90)

    val config = ResourceConfig()
    config.packages("org")
    val servlet = ServletHolder(ServletContainer(config))

    val context = ServletContextHandler(server, "/*")
    context.addServlet(servlet, "/*")

    //server.setHandler(HelloHandler());

    server.start()
    server.dumpStdErr()
    server.join()
}