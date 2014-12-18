package de.rwth.idsg.steve;


import de.rwth.idsg.steve.config.BeanConfiguration;
import de.rwth.idsg.steve.config.OcppConfiguration;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.jetty.rewrite.handler.RedirectPatternRule;
import org.eclipse.jetty.rewrite.handler.RewriteHandler;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.HttpConfiguration;
import org.eclipse.jetty.server.HttpConnectionFactory;
import org.eclipse.jetty.server.SecureRequestCustomizer;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.server.SslConnectionFactory;
import org.eclipse.jetty.server.handler.HandlerCollection;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.eclipse.jetty.util.thread.QueuedThreadPool;
import org.eclipse.jetty.util.thread.ScheduledExecutorScheduler;
import org.eclipse.jetty.webapp.WebAppContext;
import org.springframework.core.io.ClassPathResource;
import org.springframework.web.context.ContextLoaderListener;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.DispatcherServlet;

import java.io.IOException;

/**
 * @author Sevket Goekay <goekay@dbis.rwth-aachen.de>
 * @since 12.12.2014
 */
@Slf4j
public class JettyServer {

    private Server server;

    public static void main(String[] args) throws IOException {
        JettyServer jettyServer = new JettyServer();
        jettyServer.start();
    }

    /**
     * A fully configured Jetty Server instance
     */
    public JettyServer() throws IOException {

        // === jetty.xml ===
        // Setup Threadpool
        QueuedThreadPool threadPool = new QueuedThreadPool();
        threadPool.setMinThreads(SteveConfiguration.Jetty.MIN_THREADS);
        threadPool.setMaxThreads(SteveConfiguration.Jetty.MAX_THREADS);

        // Server
        server = new Server(threadPool);

        // Scheduler
        server.addBean(new ScheduledExecutorScheduler());

        // HTTP Configuration
        HttpConfiguration httpConfig = new HttpConfiguration();
        httpConfig.setSecureScheme("https");
        httpConfig.setSecurePort(SteveConfiguration.Jetty.SSL_SERVER_PORT);
        httpConfig.setOutputBufferSize(32768);
        httpConfig.setRequestHeaderSize(8192);
        httpConfig.setResponseHeaderSize(8192);
        httpConfig.setSendServerVersion(false);
        httpConfig.setSendDateHeader(false);

        // Extra options
        server.setDumpAfterStart(false);
        server.setDumpBeforeStop(false);
        server.setStopAtShutdown(true);
        server.setStopTimeout(SteveConfiguration.Jetty.STOP_TIMEOUT);

        // === jetty-http.xml ===
        ServerConnector http = new ServerConnector(server, new HttpConnectionFactory(httpConfig));
        http.setHost(SteveConfiguration.Jetty.SERVER_HOST);
        http.setPort(SteveConfiguration.Jetty.SERVER_PORT);
        http.setIdleTimeout(SteveConfiguration.Jetty.IDLE_TIMEOUT);
        server.addConnector(http);

        if (SteveConfiguration.Jetty.SSL_ENABLED) {
            // === jetty-https.xml ===
            // SSL Context Factory
            SslContextFactory sslContextFactory = new SslContextFactory();
            sslContextFactory.setKeyStorePath(SteveConfiguration.Jetty.KEY_STORE_PATH);
            sslContextFactory.setKeyStorePassword(SteveConfiguration.Jetty.KEY_STORE_PASSWORD);
            sslContextFactory.setKeyManagerPassword(SteveConfiguration.Jetty.KEY_STORE_PASSWORD);
            sslContextFactory.setExcludeCipherSuites(
                    "SSL_RSA_WITH_DES_CBC_SHA",
                    "SSL_DHE_RSA_WITH_DES_CBC_SHA",
                    "SSL_DHE_DSS_WITH_DES_CBC_SHA",
                    "SSL_RSA_EXPORT_WITH_RC4_40_MD5",
                    "SSL_RSA_EXPORT_WITH_DES40_CBC_SHA",
                    "SSL_DHE_RSA_EXPORT_WITH_DES40_CBC_SHA",
                    "SSL_DHE_DSS_EXPORT_WITH_DES40_CBC_SHA");

            // SSL HTTP Configuration
            HttpConfiguration httpsConfig = new HttpConfiguration(httpConfig);
            httpsConfig.addCustomizer(new SecureRequestCustomizer());

            // SSL Connector
            ServerConnector https = new ServerConnector(server,
                    new SslConnectionFactory(sslContextFactory, "http/1.1"),
                    new HttpConnectionFactory(httpsConfig));
            https.setPort(SteveConfiguration.Jetty.SSL_SERVER_PORT);
            https.setHost(SteveConfiguration.Jetty.SERVER_HOST);
            https.setIdleTimeout(SteveConfiguration.Jetty.IDLE_TIMEOUT);
            server.addConnector(https);
        }

        HandlerCollection handlerCollection = new HandlerCollection();
        handlerCollection.setHandlers(
                new Handler[] {
                        getRedirectHandler(),
                        getJettyContext(getSpringContext())
        });

        server.setHandler(handlerCollection);
    }

    private WebAppContext getJettyContext(WebApplicationContext springContext) throws IOException {
        WebAppContext jettyContext = new WebAppContext();
        jettyContext.setContextPath(SteveConfiguration.Jetty.CONTEXT_PATH);
        jettyContext.setResourceBase(new ClassPathResource("webapp").getURI().toString());

        // Disable directory listings if no index.html is found.
        jettyContext.setInitParameter("org.eclipse.jetty.servlet.Default.dirAllowed", "false");

        ServletHolder web = new ServletHolder("spring-dispatcher", new DispatcherServlet(springContext));
        ServletHolder cxf = new ServletHolder("cxf", JettyServlets.getApacheCXF());

        jettyContext.addEventListener(new ContextLoaderListener(springContext));
        jettyContext.addServlet(web, SteveConfiguration.SPRING_WEB_MAPPING);
        jettyContext.addServlet(cxf, SteveConfiguration.CXF_MAPPING);
        return jettyContext;
    }

    private WebApplicationContext getSpringContext() {
        AnnotationConfigWebApplicationContext ctx = new AnnotationConfigWebApplicationContext();
        ctx.register(BeanConfiguration.class);
        ctx.register(OcppConfiguration.class);
        return ctx;
    }

    private Handler getRedirectHandler() {
        RewriteHandler rewrite = new RewriteHandler();
        rewrite.setRewriteRequestURI(true);
        rewrite.setRewritePathInfo(true);
        rewrite.setOriginalPathAttribute("requestedPath");

        RedirectPatternRule baseToHome = new RedirectPatternRule();
        baseToHome.setPattern("");
        baseToHome.setLocation("/steve/manager/home");

        rewrite.addRule(baseToHome);
        return rewrite;
    }

    /**
     * Starts the Jetty Server instance
     */
    private void start() {
        try {
            server.start();
            server.join();
        } catch (InterruptedException ie) {
            log.error("Exception happened", ie);
        } catch (Exception e) {
            log.error("Failed to start Jetty server.", e);
        }
    }

    /**
     * Stops the Jetty Server instance
     */
    private void stop() {
        new Thread() {
            @Override
            public void run() {
                try {
                    server.stop();
                } catch (Exception e) {
                    log.error("Failed to stop Jetty server.", e);
                }
            }
        }.start();
    }
}