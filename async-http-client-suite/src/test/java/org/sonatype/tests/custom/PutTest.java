package org.sonatype.tests.custom;

/*
 * Copyright (c) 2010 Sonatype, Inc. All rights reserved.
 *
 * This program is licensed to you under the Apache License Version 2.0, 
 * and you may not use this file except in compliance with the Apache License Version 2.0. 
 * You may obtain a copy of the Apache License Version 2.0 at http://www.apache.org/licenses/LICENSE-2.0.
 *
 * Unless required by applicable law or agreed to in writing, 
 * software distributed under the Apache License Version 2.0 is distributed on an 
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. 
 * See the Apache License Version 2.0 for the specific language governing permissions and limitations there under.
 */

import static org.junit.Assert.*;

import java.io.File;
import java.io.IOException;

import org.junit.AfterClass;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.sonatype.tests.async.util.AsyncSuiteConfiguration;
import org.sonatype.tests.jetty.runner.ConfigurationRunner;
import org.sonatype.tests.jetty.runner.ConfigurationRunner.ConfiguratorList;
import org.sonatype.tests.jetty.server.behaviour.Consumer;
import org.sonatype.tests.jetty.server.behaviour.ErrorBehaviour;
import org.sonatype.tests.jetty.server.util.FileUtil;
import org.sonatype.tests.server.api.ServerProvider;

import com.ning.http.client.AsyncHttpClient;
import com.ning.http.client.AsyncHttpClient.BoundRequestBuilder;
import com.ning.http.client.AsyncHttpClientConfig.Builder;
import com.ning.http.client.Response;

/**
 * @author Benjamin Hanzelmann
 */
@RunWith( ConfigurationRunner.class )
@ConfiguratorList( { "DefaultSuiteConfigurator.list", "AuthSuiteConfigurator.list" } )
public class PutTest
    extends AsyncSuiteConfiguration
{

    private static Logger logger = org.slf4j.LoggerFactory.getLogger( PutTest.class );

    private static File largeFile;

    private static long heapSize;


    @AfterClass
    public static void afterClass()
        throws IOException
    {
        FileUtil.delete( largeFile );
        largeFile = null;
    }

    @Override
    public void before()
        throws Exception
    {
        super.before();
        setAuthentication( "user", "password", false );
    }

    @Test
    @Ignore( "AHC error?" )
    public void testPutLargeFile()
        throws Exception
    {
        if ( largeFile == null )
        {
            byte[] bytes = "RatherLargeFileRatherLargeFileRatherLargeFileRatherLargeFile".getBytes( "UTF-16" );
            heapSize = Runtime.getRuntime().maxMemory();
            logger.debug( "creating file of size ~" + heapSize );
            long repeats = ( heapSize / bytes.length ) + 1;
            largeFile = FileUtil.createTempFile( bytes, (int) repeats );
            logger.debug( "created file of size " + largeFile.length() );
        }
        Consumer consumer = new Consumer();
        provider().addBehaviour( "/consume/*", consumer );

        String url = url( "consume", "foo" );

        Builder cfg = new Builder();
        cfg.setIdleConnectionTimeoutInMs( (int) heapSize );
        cfg.setConnectionTimeoutInMs( (int) heapSize );
        cfg.setRequestTimeoutInMs( (int) heapSize );
        AsyncHttpClient c = new AsyncHttpClient( cfg.build() );

        BoundRequestBuilder put = c.preparePut( url );
        put.setBody( largeFile );

        execute( put );

        assertEquals( largeFile.length(), consumer.getTotal() );

    }

    @Test
    public void testPutFile()
        throws Exception
    {
        Consumer consumer = new Consumer();
        provider().addBehaviour( "/testPutFile/*", consumer );

        String url = url( "testPutFile", "foo" );

        BoundRequestBuilder put = client().preparePut( url );
        File f = FileUtil.createTempFile( "This is a file." );
        put.setBody( f );
        execute( put );

        try
        {
            assertEquals( f.length(), consumer.getTotal() );
        }
        finally
        {
            FileUtil.delete( f );
        }
    }

    @Test
    public void testPutBytes()
        throws Exception
    {
        Consumer consumer = new Consumer();
        provider().addBehaviour( "/put/*", consumer );
        String url = url( "put", "someData" );
        BoundRequestBuilder rb = client().preparePut( url );
        byte[] bytes = "datavalue".getBytes( "UTF-8" );
        rb.setBody( bytes );

        Response response = execute( rb );

        assertEquals( 200, response.getStatusCode() );
        assertEquals( bytes.length, consumer.getTotal() );
    }

    @Test
    public void testPutError()
        throws Exception
    {

        String url = url( "methodsupported", "501", "errormsg" );
        BoundRequestBuilder rb = client().preparePut( url );
        Response response = execute( rb );

        assertEquals( 501, response.getStatusCode() );
        assertEquals( "errormsg", response.getStatusText() );
    }

    @Override
    public void configureProvider( ServerProvider provider )
    {
        super.configureProvider( provider );
        provider.addBehaviour( "/methodsupported/*", new ErrorBehaviour( 501, "errormsg" ) );
    }

}
