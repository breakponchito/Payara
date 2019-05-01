/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2016-2019 Payara Foundation and/or its affiliates. All rights reserved.
 *
 * The contents of this file are subject to the terms of either the GNU
 * General Public License Version 2 only ("GPL") or the Common Development
 * and Distribution License("CDDL") (collectively, the "License").  You
 * may not use this file except in compliance with the License.  You can
 * obtain a copy of the License at
 * https://github.com/payara/Payara/blob/master/LICENSE.txt
 * See the License for the specific
 * language governing permissions and limitations under the License.
 *
 * When distributing the software, include this License Header Notice in each
 * file and include the License file at glassfish/legal/LICENSE.txt.
 *
 * GPL Classpath Exception:
 * The Payara Foundation designates this particular file as subject to the "Classpath"
 * exception as provided by the Payara Foundation in the GPL Version 2 section of the License
 * file that accompanied this code.
 *
 * Modifications:
 * If applicable, add the following below the License Header, with the fields
 * enclosed by brackets [] replaced by your own identifying information:
 * "Portions Copyright [year] [name of copyright owner]"
 *
 * Contributor(s):
 * If you wish your version of this file to be governed by only the CDDL or
 * only the GPL Version 2, indicate your decision by adding "[Contributor]
 * elects to include this software in this distribution under the [CDDL or GPL
 * Version 2] license."  If you don't indicate a single choice of license, a
 * recipient has the option to distribute your version of this file under
 * either the CDDL, the GPL Version 2 or to extend the choice of license to
 * its licensees as provided above.  However, if you add GPL Version 2 code
 * and therefore, elected the GPL Version 2 license, then the option applies
 * only if the new code is made subject to such option by the copyright
 * holder.
 */
package fish.payara.nucleus.notification.domain;

import com.sun.enterprise.config.serverbeans.Server;
import fish.payara.notification.healthcheck.HealthCheckResultEntry;
import fish.payara.notification.requesttracing.RequestTrace;
import fish.payara.nucleus.notification.configuration.NotifierType;
import fish.payara.nucleus.notification.service.NotificationEventFactoryStore;
import org.glassfish.api.admin.ServerEnvironment;
import org.glassfish.hk2.api.ServiceLocator;
import org.jvnet.hk2.annotations.Contract;

import javax.inject.Inject;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.text.MessageFormat;
import java.util.List;
import java.util.logging.Level;

/**
 * Factory for building {@link NotificationEvent}
 * @author mertcaliskan
 * @since 4.1.2.171
 */
@Contract
public abstract class NotificationEventFactory<E extends NotificationEvent> {

    @Inject
    NotificationEventFactoryStore store;

    @Inject
    private ServerEnvironment environment;

    @Inject
    private ServiceLocator habitat;

    protected void registerEventFactory(NotifierType type, NotificationEventFactory notificationEventFactory) {
        getStore().register(type, notificationEventFactory);
    }

    protected E initializeEvent(E e) {
        try {
            e.setHostName(InetAddress.getLocalHost().getHostName());
        } catch (UnknownHostException ex) {
            //No-op
        }
        e.setDomainName(environment.getDomainName());
        e.setInstanceName(environment.getInstanceName());
        Server server = habitat.getService(Server.class, environment.getInstanceName());
        e.setServerName(server.getName());

        return e;
    }

    protected abstract E createEventInstance();

    /**
     * Creates a {@link NotificationEvent}
     * @param subject Subject of the message
     * i.e. what the subject line is if the event is sent to the Javamail notifier
     * @param message The message text of the event
     * @return the resulting {@link NotificationEvent}
     */
    public E buildNotificationEvent(String subject, String message) {
        E event = initializeEvent(createEventInstance());
        event.setSubject(subject);
        event.setMessage(message);

        return event;
    }

    public E buildNotificationEvent(String subject, RequestTrace requestTrace) {
        E event = initializeEvent(createEventInstance());
        event.setSubject(subject);
        event.setMessage(requestTrace.toString());

        return event;
    }

    public E buildNotificationEvent(String name, List<HealthCheckResultEntry> entries, Level level) {
        E event = initializeEvent(createEventInstance());
        event.setSubject(getSubject(level));
        String messageFormatted = getMessageFormatted(new Object[]{name, getCumulativeMessages(entries)});
        if (messageFormatted != null) {
            event.setMessage(messageFormatted);
        }

        return event;
    }

    /**
     * Creates a {@link NotificationEvent}
     * @param level Severity level of notification. This is unused in the base factory
     * @param subject Subject of the message
     * i.e. what the subject line is if the event is sent to the Javamail notifier
     * @param message The message text of the event
     * @param parameters An additional parameters to be formatted as part of the message
     * @return the resulting {@link NotificationEvent}
     */
    public E buildNotificationEvent(Level level, String subject, String message, Object[] parameters) {
        E event = initializeEvent(createEventInstance());
        event.setSubject(subject);
        if (parameters != null && parameters.length > 0) {
            message = MessageFormat.format(message, parameters);
        }
        event.setMessage(message);
        return event;
    }

    public NotificationEventFactoryStore getStore() {
        return store;
    }

    protected String getSubject(Level level) {
        return "Health Check notification with severity level: " + level.getName();
    }

    protected String getMessageFormatted(Object[] parameters) {
        String formattedMessage = null;
        if (parameters != null && parameters.length > 0) {
            formattedMessage = MessageFormat.format("{0}:{1}", parameters);
        }
        return formattedMessage;
    }

    protected String getCumulativeMessages(List<HealthCheckResultEntry> entries) {
        return "Health Check Result:" + entries.toString();
    }
}