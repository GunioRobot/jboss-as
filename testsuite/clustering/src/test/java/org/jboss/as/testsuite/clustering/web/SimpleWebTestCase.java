/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2011, Red Hat, Inc., and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */

package org.jboss.as.testsuite.clustering.web;

import java.io.IOException;

import javax.servlet.http.HttpServletResponse;

import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.shrinkwrap.api.Archive;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Validate the <distributable/> works for single node
 * @author Paul Ferraro
 */
@RunWith(Arquillian.class)
@RunAsClient
public class SimpleWebTestCase {
    @Deployment
    public static Archive<?> deployment() {
        WebArchive war = ShrinkWrap.create(WebArchive.class, "distributable.war");
        war.addClass(SimpleServlet.class);
        war.setWebXML("distributable.war/WEB-INF/web.xml");
        System.out.println(war.toString(true));
        return war;
    }

    @Test
    public void test() throws ClientProtocolException, IOException {
        DefaultHttpClient client = new DefaultHttpClient();
        try {
            HttpResponse response = client.execute(new HttpGet("http://localhost:8080/distributable/simple"));
            Assert.assertEquals(HttpServletResponse.SC_OK, response.getStatusLine().getStatusCode());
            Assert.assertEquals(Integer.parseInt(response.getFirstHeader("value").getValue()), 1);
            Assert.assertFalse(Boolean.valueOf(response.getFirstHeader("serialized").getValue()));
            response.getEntity().getContent().close();

            response = client.execute(new HttpGet("http://localhost:8080/distributable/simple"));
            Assert.assertEquals(HttpServletResponse.SC_OK, response.getStatusLine().getStatusCode());
            Assert.assertEquals(Integer.parseInt(response.getFirstHeader("value").getValue()), 2);
            // This won't be true unless we have somewhere to which to replicate
            Assert.assertFalse(Boolean.valueOf(response.getFirstHeader("serialized").getValue()));
            response.getEntity().getContent().close();
        } finally {
            client.getConnectionManager().shutdown();
        }
    }
}
