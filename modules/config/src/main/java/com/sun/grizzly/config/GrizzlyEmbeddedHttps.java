/*
 * The contents of this file are subject to the terms
 * of the Common Development and Distribution License
 * (the License).  You may not use this file except in
 * compliance with the License.
 *
 * You can obtain a copy of the license at
 * https://glassfish.dev.java.net/public/CDDLv1.0.html or
 * glassfish/bootstrap/legal/CDDLv1.0.txt.
 * See the License for the specific language governing
 * permissions and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL
 * Header Notice in each file and include the License file
 * at glassfish/bootstrap/legal/CDDLv1.0.txt.
 * If applicable, add the following below the CDDL Header,
 * with the fields enclosed by brackets [] replaced by
 * you own identifying information:
 * "Portions Copyrighted [year] [name of copyright owner]"
 *
 * Copyright 2006 Sun Microsystems, Inc. All rights reserved.
 */
package com.sun.grizzly.config;

import com.sun.grizzly.ProtocolChain;
import com.sun.grizzly.ProtocolChainInstanceHandler;
import com.sun.grizzly.ProtocolFilter;
import com.sun.grizzly.SSLConfig;
import com.sun.grizzly.TCPSelectorHandler;
import com.sun.grizzly.config.dom.NetworkListener;
import com.sun.grizzly.config.dom.Protocol;
import com.sun.grizzly.config.dom.Ssl;
import com.sun.grizzly.filter.SSLReadFilter;
import com.sun.grizzly.http.ProcessorTask;
import com.sun.grizzly.ssl.SSLAsyncProcessorTask;
import com.sun.grizzly.ssl.SSLAsyncProtocolFilter;
import com.sun.grizzly.ssl.SSLDefaultProtocolFilter;
import com.sun.grizzly.ssl.SSLProcessorTask;
import com.sun.grizzly.ssl.SSLSelectorThreadHandler;
import com.sun.grizzly.util.ClassLoaderUtil;
import com.sun.grizzly.util.net.SSLImplementation;
import com.sun.grizzly.util.net.ServerSocketFactory;
import org.jvnet.hk2.component.Habitat;

import javax.net.ssl.SSLContext;
import java.util.LinkedList;
import java.util.List;
import java.util.logging.Level;

/**
 * Implementation of Grizzly embedded HTTPS listener
 *
 * @author Jeanfrancois Arcand
 * @author Alexey Stashok
 */
public class GrizzlyEmbeddedHttps extends GrizzlyEmbeddedHttp {
    /**
     * The <code>SSLImplementation</code>
     */
    private SSLImplementation sslImplementation;
    /**
     * The <code>SSLContext</code> associated with the SSL implementation we are running on.
     */
    protected SSLContext sslContext;
    /**
     * The list of cipher suite
     */
    private String[] enabledCipherSuites = null;
    /**
     * the list of protocols
     */
    private String[] enabledProtocols = null;
    /**
     * Client mode when handshaking.
     */
    private boolean clientMode = false;
    /**
     * Require client Authentication.
     */
    private boolean needClientAuth = false;
    /**
     * True when requesting authentication.
     */
    private boolean wantClientAuth = false;

    public GrizzlyEmbeddedHttps(GrizzlyServiceListener grizzlyServiceListener) {
        super(grizzlyServiceListener);
    }
    // ---------------------------------------------------------------------/.

    @Override
    protected ProtocolChainInstanceHandler configureProtocol(NetworkListener networkListener, Protocol protocol, Habitat habitat,
            boolean mayEnableComet) {
        if (protocol.getHttp() != null && toBoolean(protocol.getSecurityEnabled())) {
            configureSSL(protocol.getSsl());
        }

        return super.configureProtocol(networkListener, protocol, habitat,
                mayEnableComet);
    }

    /**
     * Configures the SSL properties on the given PECoyoteConnector from the SSL config of the given HTTP listener.
     *
     * @param ssl
     */
    private boolean configureSSL(final Ssl ssl) {
        final List<String> tmpSSLArtifactsList = new LinkedList<String>();
        if (ssl != null) {
            if (ssl.getCrlFile() != null) {
                setProperty("crlFile", ssl.getCrlFile());
            }
            if (ssl.getTrustAlgorithm() != null) {
                setProperty("trustAlgorithm", ssl.getTrustAlgorithm());
            }
            if (ssl.getTrustMaxCertLengthBytes() != null) {
                setProperty("trustMaxCertLength", ssl.getTrustMaxCertLengthBytes());
            }
            configSslOptions(ssl);
            
            // client-auth
            if (Boolean.parseBoolean(ssl.getClientAuthEnabled())) {
                setNeedClientAuth(true);
            }
            // ssl protocol variants
            if (Boolean.parseBoolean(ssl.getSsl2Enabled())) {
                tmpSSLArtifactsList.add("SSLv2");
            }
            if (Boolean.parseBoolean(ssl.getSsl3Enabled())) {
                tmpSSLArtifactsList.add("SSLv3");
            }
            if (Boolean.parseBoolean(ssl.getTlsEnabled())) {
                tmpSSLArtifactsList.add("TLSv1");
            }
            if (Boolean.parseBoolean(ssl.getSsl3Enabled()) ||
                Boolean.parseBoolean(ssl.getTlsEnabled())) {
                tmpSSLArtifactsList.add("SSLv2Hello");
            }
            if (tmpSSLArtifactsList.isEmpty()) {
                logger.log(Level.WARNING, "pewebcontainer.all_ssl_protocols_disabled",
                    ((Protocol) ssl.getParent()).getName());
            } else {
                final String[] protocols = new String[tmpSSLArtifactsList.size()];
                tmpSSLArtifactsList.toArray(protocols);
                setEnabledProtocols(protocols);
            }
            String auth = ssl.getClientAuth();
            if (auth != null) {
                if ("want".equalsIgnoreCase(auth.trim())) {
                    setWantClientAuth(true);
                } else if ("need".equalsIgnoreCase(auth.trim())) {
                    setNeedClientAuth(true);
                }
            }
            if (ssl.getClassname() != null) {
                SSLImplementation impl = (SSLImplementation) ClassLoaderUtil.load(ssl.getClassname());
                if (impl != null) {
                    setSSLImplementation(impl);
                } else {
                    logger.log(Level.WARNING, "Unable to load SSLImplementation");

                }
            }
            tmpSSLArtifactsList.clear();
            // ssl3-tls-ciphers
            final String ssl3Ciphers = ssl.getSsl3TlsCiphers();
            if (ssl3Ciphers != null && ssl3Ciphers.length() > 0) {
                final String[] ssl3CiphersArray = ssl3Ciphers.split(",");
                for (final String cipher : ssl3CiphersArray) {
                    tmpSSLArtifactsList.add(cipher.trim());
                }
            }
            // ssl2-tls-ciphers
            final String ssl2Ciphers = ssl.getSsl2Ciphers();
            if (ssl2Ciphers != null && ssl2Ciphers.length() > 0) {
                final String[] ssl2CiphersArray = ssl2Ciphers.split(",");
                for (final String cipher : ssl2CiphersArray) {
                    tmpSSLArtifactsList.add(cipher.trim());
                }
            }
            if (tmpSSLArtifactsList.isEmpty()) {
                logger.log(Level.WARNING, "pewebcontainer.all_ssl_ciphers_disabled",
                    ((Protocol) ssl.getParent()).getName());
            } else {
                final String[] enabledCiphers = new String[tmpSSLArtifactsList.size()];
                tmpSSLArtifactsList.toArray(enabledCiphers);
                setEnabledCipherSuites(enabledCiphers);
            }
            try {
                initializeSSL(ssl);
                return true;
            } catch (Exception e) {
                logger.log(Level.WARNING, "SSL support could not be configured!", e);
            }
        }
        if (tmpSSLArtifactsList.isEmpty()) {
            logger.log(Level.WARNING, "pewebcontainer.all_ssl_ciphers_disabled");
        } else {
            final String[] enabledCiphers = new String[tmpSSLArtifactsList.size()];
            tmpSSLArtifactsList.toArray(enabledCiphers);
            setEnabledCipherSuites(enabledCiphers);
        }
        try {
            initializeSSL(ssl);
            return true;
        } catch (Exception e) {
            logger.log(Level.WARNING, "SSL support could not be configured!", e);
        }
        return false;
    }


    private void configSslOptions(Ssl ssl) {
        if (ssl != null) {
            if (ssl.getCrlFile() != null) {
                setProperty("crlFile", ssl.getCrlFile());
            }
            if (ssl.getTrustAlgorithm() != null) {
                setProperty("trustAlgorithm", ssl.getTrustAlgorithm());
            }
            if (ssl.getTrustMaxCertLengthBytes() != null) {
                setProperty("trustMaxCertLength", ssl.getTrustMaxCertLengthBytes());
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected TCPSelectorHandler createSelectorHandler() {
        return new SSLSelectorThreadHandler(this);
    }

    /**
     * Create HTTP parser <code>ProtocolFilter</code>
     *
     * @return HTTP parser <code>ProtocolFilter</code>
     */
    @Override
    protected ProtocolFilter createHttpParserFilter() {
        if (asyncExecution) {
            return new SSLAsyncProtocolFilter(algorithmClass, port, sslImplementation);
        } else {
            return new SSLDefaultProtocolFilter(algorithmClass, port, sslImplementation);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void configureFilters(final ProtocolChain protocolChain) {
        if (portUnificationFilter != null) {
            portUnificationFilter.setContinuousExecution(false);
            protocolChain.addFilter(portUnificationFilter);
        } else {
            protocolChain.addFilter(createReadFilter());
        }
        
        protocolChain.addFilter(createHttpParserFilter());
    }

    /**
     * Create and configure <code>SSLReadFilter</code>
     *
     * @return <code>SSLReadFilter</code>
     */
    @Override
    protected ProtocolFilter createReadFilter() {
        final SSLReadFilter readFilter = new SSLReadFilter();
        readFilter.setSSLContext(sslContext);
        readFilter.setClientMode(clientMode);
        readFilter.setEnabledCipherSuites(enabledCipherSuites);
        readFilter.setEnabledProtocols(enabledProtocols);
        readFilter.setNeedClientAuth(needClientAuth);
        readFilter.setWantClientAuth(wantClientAuth);
        return readFilter;
    }

    /**
     * Create <code>SSLProcessorTask</code> objects and configure it to be ready to proceed request.
     */
    @Override
    protected ProcessorTask newProcessorTask(final boolean initialize) {
        SSLProcessorTask t = (asyncExecution
            ? new SSLAsyncProcessorTask(initialize, getBufferResponse())
            : new SSLProcessorTask(initialize, getBufferResponse()));
        configureProcessorTask(t);
        return t;
    }

    /**
     * Set the SSLContext required to support SSL over NIO.
     */
    public void setSSLConfig(final SSLConfig sslConfig) {
        sslContext = sslConfig.createSSLContext();
    }

    /**
     * Set the SSLContext required to support SSL over NIO.
     */
    public void setSSLContext(final SSLContext sslContext) {
        this.sslContext = sslContext;
    }

    /**
     * Return the SSLContext required to support SSL over NIO.
     */
    public SSLContext getSSLContext() {
        return sslContext;
    }

    /**
     * Set the Coyote SSLImplementation.
     */
    public void setSSLImplementation(final SSLImplementation sslImplementation) {
        this.sslImplementation = sslImplementation;
    }

    /**
     * Return the current <code>SSLImplementation</code> this Thread
     */
    public SSLImplementation getSSLImplementation() {
        return sslImplementation;
    }

    /**
     * Returns the list of cipher suites to be enabled when {@link SSLEngine} is initialized.
     *
     * @return <tt>null</tt> means 'use {@link SSLEngine}'s default.'
     */
    public String[] getEnabledCipherSuites() {
        return enabledCipherSuites;
    }

    /**
     * Sets the list of cipher suites to be enabled when {@link SSLEngine} is initialized.
     *
     * @param enabledCipherSuites <tt>null</tt> means 'use {@link SSLEngine}'s default.'
     */
    public void setEnabledCipherSuites(final String[] enabledCipherSuites) {
        this.enabledCipherSuites = enabledCipherSuites;
    }

    /**
     * Returns the list of protocols to be enabled when {@link SSLEngine} is initialized.
     *
     * @return <tt>null</tt> means 'use {@link SSLEngine}'s default.'
     */
    public String[] getEnabledProtocols() {
        return enabledProtocols;
    }

    /**
     * Sets the list of protocols to be enabled when {@link SSLEngine} is initialized.
     *
     * @param enabledProtocols <tt>null</tt> means 'use {@link SSLEngine}'s default.'
     */
    public void setEnabledProtocols(final String[] enabledProtocols) {
        this.enabledProtocols = enabledProtocols;
    }

    /**
     * Returns <tt>true</tt> if the SSlEngine is set to use client mode when handshaking.
     *
     * @return is client mode enabled
     */
    public boolean isClientMode() {
        return clientMode;
    }

    /**
     * Configures the engine to use client (or server) mode when handshaking.
     */
    public void setClientMode(final boolean clientMode) {
        this.clientMode = clientMode;
    }

    /**
     * Returns <tt>true</tt> if the SSLEngine will <em>require</em> client authentication.
     */
    public boolean isNeedClientAuth() {
        return needClientAuth;
    }

    /**
     * Configures the engine to <em>require</em> client authentication.
     */
    public void setNeedClientAuth(final boolean needClientAuth) {
        this.needClientAuth = needClientAuth;
    }

    /**
     * Returns <tt>true</tt> if the engine will <em>request</em> client authentication.
     */
    public boolean isWantClientAuth() {
        return wantClientAuth;
    }

    /**
     * Configures the engine to <em>request</em> client authentication.
     */
    public void setWantClientAuth(final boolean wantClientAuth) {
        this.wantClientAuth = wantClientAuth;
    }

    /**
     * Initializes SSL
     *
     * @param ssl
     *
     * @throws Exception
     */
    protected void initializeSSL(Ssl sslConfig) throws Exception {
        final SSLImplementation sslHelper = SSLImplementation.getInstance();
        final ServerSocketFactory serverSF = sslHelper.getServerSocketFactory();
        // key store settings
        setAttribute(serverSF, "keystore", sslConfig.getKeyStore(), "javax.net.ssl.keyStore");
        setAttribute(serverSF, "keystoreType", sslConfig.getKeyStoreType(), "javax.net.ssl.keyStoreType");
        setAttribute(serverSF, "keystorePass", sslConfig.getKeyStorePassword(), "javax.net.ssl.keyStorePassword");
        // trust store settings
        setAttribute(serverSF, "truststore", sslConfig.getTrustStore(), "javax.net.ssl.trustStore");
        setAttribute(serverSF, "truststoreType", sslConfig.getTrustStoreType(), "javax.net.ssl.trustStoreType");
        setAttribute(serverSF, "truststorePass", sslConfig.getTrustStorePassword(), "javax.net.ssl.trustStorePassword");
        // cert nick name
        serverSF.setAttribute("keyAlias", sslConfig.getCertNickname());
        serverSF.init();
        sslImplementation = sslHelper;
        sslContext = serverSF.getSSLContext();
        setHttpSecured(true);
    }

    private void setAttribute(final ServerSocketFactory serverSF, final String name, final String value,
        final String property) {
        serverSF.setAttribute(name, value == null ? System.getProperty(property) : value);
    }
}
