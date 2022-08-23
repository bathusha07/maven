package org.slf4j.simple;

/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.slf4j.Marker;
import org.slf4j.event.Level;
import org.slf4j.event.LoggingEvent;
import org.slf4j.helpers.MessageFormatter;
import org.slf4j.helpers.NormalizedParameters;

import static org.apache.maven.shared.utils.logging.MessageUtils.buffer;
import static org.apache.maven.shared.utils.logging.MessageUtils.level;

/**
 * Logger for Maven, that support colorization of levels and stacktraces.
 * This class implements 2 methods introduced in slf4j-simple provider local copy.
 *
 * @since 3.5.0
 */
public class MavenSimpleLogger
        extends SimpleLogger
{

    private static final long START_TIME = System.currentTimeMillis();
    private transient String shortLogName = null;

    MavenSimpleLogger( String name )
    {
        super( name );
    }

    /**
     * This is our internal implementation for logging regular (non-parameterized)
     * log messages.
     *
     * @param level          One of the LOG_LEVEL_XXX constants defining the log level
     * @param messagePattern The message itself
     * @param t              The exception whose stack trace should be logged
     */
    @Override
    protected void handleNormalizedLoggingCall( Level level, Marker marker, String messagePattern, Object[] arguments,
                                                Throwable t )
    {

        List<Marker> markers = null;

        if ( marker != null )
        {
            markers = new ArrayList<>();
            markers.add( marker );
        }

        innerHandleNormalizedLoggingCall( level, markers, messagePattern, arguments, t );
    }

    public void log( LoggingEvent event )
    {
        int levelInt = event.getLevel().toInt();

        if ( !isLevelEnabled( levelInt ) )
        {
            return;
        }

        NormalizedParameters np = NormalizedParameters.normalize( event );

        innerHandleNormalizedLoggingCall( event.getLevel(), event.getMarkers(), np.getMessage(), np.getArguments(),
                event.getThrowable() );
    }

    private void innerHandleNormalizedLoggingCall( Level level, List<Marker> markers, String messagePattern,
                                                   Object[] arguments, Throwable t )
    {

        StringBuilder buf = new StringBuilder( 32 );

        // Append date-time if so configured
        if ( CONFIG_PARAMS.showDateTime )
        {
            if ( CONFIG_PARAMS.dateFormatter != null )
            {
                buf.append( getFormattedDate() );
                buf.append( SP );
            }
            else
            {
                buf.append( System.currentTimeMillis() - START_TIME );
                buf.append( SP );
            }
        }

        // Append current thread name if so configured
        if ( CONFIG_PARAMS.showThreadName )
        {
            buf.append( '[' );
            buf.append( Thread.currentThread().getName() );
            buf.append( "] " );
        }

        if ( CONFIG_PARAMS.showThreadId )
        {
            buf.append( TID_PREFIX );
            buf.append( Thread.currentThread().getId() );
            buf.append( SP );
        }

        if ( CONFIG_PARAMS.levelInBrackets )
        {
            buf.append( '[' );
        }

        // Append a readable representation of the log level
        buf.append( renderLevel( level ) );
        if ( CONFIG_PARAMS.levelInBrackets )
        {
            buf.append( ']' );
        }
        buf.append( SP );

        // Append the name of the log instance if so configured
        if ( CONFIG_PARAMS.showShortLogName )
        {
            if ( shortLogName == null )
            {
                shortLogName = computeShortName();
            }
            buf.append( shortLogName ).append( " - " );
        }
        else if ( CONFIG_PARAMS.showLogName )
        {
            buf.append( String.valueOf( name ) ).append( " - " );
        }

        if ( markers != null )
        {
            buf.append( SP );
            for ( Marker marker : markers )
            {
                buf.append( marker.getName() ).append( SP );
            }
        }

        String formattedMessage = MessageFormatter.basicArrayFormat( messagePattern, arguments );

        // Append the message
        buf.append( formattedMessage );

        write( buf, t );
    }

    //    @Override
    protected String renderLevel( Level level )
    {
        switch ( level )
        {
            case TRACE:
                return level().debug( "TRACE" );
            case DEBUG:
                return level().debug( "DEBUG" );
            case INFO:
                return level().info( "INFO" );
            case WARN:
                return level().warning( "WARNING" );
            case ERROR:
            default:
                return level().error( "ERROR" );
        }
    }

    @Override
    protected void writeThrowable( Throwable t, PrintStream stream )
    {
        if ( t == null )
        {
            return;
        }
        stream.print( buffer().failure( t.getClass().getName() ) );
        if ( t.getMessage() != null )
        {
            stream.print( ": " );
            stream.print( buffer().failure( t.getMessage() ) );
        }
        stream.println();

        while ( t != null )
        {
            for ( StackTraceElement e : t.getStackTrace() )
            {
                stream.print( "    " );
                stream.print( buffer().strong( "at" ) );
                stream.print( " " + e.getClassName() + "." + e.getMethodName() );
                stream.print( buffer().a( " (" ).strong( getLocation( e ) ).a( ")" ) );
                stream.println();
            }

            t = t.getCause();
            if ( t != null )
            {
                stream.print( buffer().strong( "Caused by" ).a( ": " ).a( t.getClass().getName() ) );
                if ( t.getMessage() != null )
                {
                    stream.print( ": " );
                    stream.print( buffer().failure( t.getMessage() ) );
                }
                stream.println();
            }
        }
    }

    private String getLocation( final StackTraceElement e )
    {
        assert e != null;

        if ( e.isNativeMethod() )
        {
            return "Native Method";
        }
        else if ( e.getFileName() == null )
        {
            return "Unknown Source";
        }
        else if ( e.getLineNumber() >= 0 )
        {
            return String.format( "%s:%s", e.getFileName(), e.getLineNumber() );
        }
        else
        {
            return e.getFileName();
        }
    }

    private String computeShortName()
    {
        return name.substring( name.lastIndexOf( "." ) + 1 );
    }

    private String getFormattedDate()
    {
        Date now = new Date();
        String dateText;
        synchronized ( CONFIG_PARAMS.dateFormatter )
        {
            dateText = CONFIG_PARAMS.dateFormatter.format( now );
        }
        return dateText;
    }
}
