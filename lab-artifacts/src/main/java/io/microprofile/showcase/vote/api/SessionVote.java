/*
 * Copyright 2016 Microprofile.io
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.microprofile.showcase.vote.api;

import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static javax.ws.rs.core.MediaType.TEXT_HTML;
import static javax.ws.rs.core.MediaType.TEXT_PLAIN;

import java.util.Calendar;
import java.util.Collection;

import javax.annotation.PostConstruct;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.ws.rs.BadRequestException;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.NotFoundException;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Response;

import org.eclipse.microprofile.metrics.annotation.Counted;
import org.eclipse.microprofile.metrics.annotation.Metered;

import io.microprofile.showcase.vote.health.HealthCheckBean;
import io.microprofile.showcase.vote.model.Attendee;
import io.microprofile.showcase.vote.model.SessionRating;
import io.microprofile.showcase.vote.persistence.AttendeeDAO;
import io.microprofile.showcase.vote.persistence.NonPersistent;
import io.microprofile.showcase.vote.persistence.Persistent;
import io.microprofile.showcase.vote.persistence.SessionRatingDAO;
import io.microprofile.showcase.vote.utils.Log;

@ApplicationScoped
@Path("/")
@Log
@Metered(name="io.microprofile.showcase.vote.api.SessionVote.Type.Metered",tags="app=vote")
public class SessionVote {

    private @Inject @NonPersistent AttendeeDAO hashMapAttendeeDAO;
    private @Inject @Persistent AttendeeDAO couchDBAttendeeDAO;

    private @Inject @NonPersistent SessionRatingDAO hashMapSessionRatingDAO;
    private @Inject @Persistent SessionRatingDAO couchDBSessionRatingDAO;
    
    private AttendeeDAO selectedAttendeeDAO;
    private SessionRatingDAO selectedSessionRatingDAO;
    
    private @Inject HealthCheckBean healthCheckBean;
    
    @PostConstruct
    @Counted(name="io.microprofile.showcase.vote.api.SessionVote.PostConstruct.connectToDAO.monotonic.absolute(true)",monotonic=true,absolute=true,tags="app=vote")
    private void connectToDAO() {
        if (couchDBAttendeeDAO.isAccessible()) {
            selectedAttendeeDAO = couchDBAttendeeDAO;
            selectedSessionRatingDAO = couchDBSessionRatingDAO;
        } else {
            selectedAttendeeDAO = hashMapAttendeeDAO;
            selectedSessionRatingDAO = hashMapSessionRatingDAO;
        }
    }

    @GET
    @Path("/")
    @Produces(TEXT_HTML)
    public String info() {
        return "Microservice Session Vote Application";
    }

    public void setAttendeeSessionRating(AttendeeDAO attendee, SessionRatingDAO rating) {
        this.selectedAttendeeDAO = attendee;
        this.selectedSessionRatingDAO = rating;
    }

    @POST
    @Path("/attendee")
    @Produces(APPLICATION_JSON)
    @Consumes(APPLICATION_JSON)
    @Counted(name="io.microprofile.showcase.vote.api.SessionVote.registerAttendee.monotonic.absolute(true)",monotonic=true,absolute=true,tags="app=vote")
    public Attendee registerAttendee(Attendee name) {
        Attendee attendee = selectedAttendeeDAO.createNewAttendee(name);
        return attendee;
    }

    @PUT
    @Path("/attendee/{id}")
    @Produces(APPLICATION_JSON)
    @Consumes(APPLICATION_JSON)
    public Attendee updateAttendee(@PathParam("id") String id, Attendee attendee) {
        Attendee original = getAttendee(id);
        original.setName(attendee.getName());
        Attendee updated = selectedAttendeeDAO.updateAttendee(original);
        return updated;
    }

    @GET
    @Path("/attendee")
    @Produces(APPLICATION_JSON)
    @Counted(name="io.microprofile.showcase.vote.api.SessionVote.getAllAttendees.monotonic.absolute(true)",monotonic=true,absolute=true,tags="app=vote")
    public Collection<Attendee> getAllAttendees() {
        return selectedAttendeeDAO.getAllAttendees();
    }

    @GET
    @Path("/attendee/{id}")
    @Produces(APPLICATION_JSON)
    @Counted(name="io.microprofile.showcase.vote.api.SessionVote.getAttendee.monotonic.absolute(true)",monotonic=true,absolute=true,tags="app=vote")
    public Attendee getAttendee(@PathParam("id") String id) {
        Attendee attendee = selectedAttendeeDAO.getAttendee(id);
        if (attendee == null) {
            throw new NotFoundException("Attendee not found: " + id);
        }
        return attendee;
    }

    @DELETE
    @Path("/attendee/{id}")
    @Produces(APPLICATION_JSON)
    public void deleteAttendee(@PathParam("id") String id) {
        Attendee attendee = getAttendee(id);
        Collection<SessionRating> ratings = votesByAttendee(attendee.getId());
        for (SessionRating rating : ratings) {
            selectedSessionRatingDAO.deleteRating(rating.getId());
        }
        selectedAttendeeDAO.deleteAttendee(attendee.getId());
    }

    @POST
    @Path("/rate")
    @Produces(APPLICATION_JSON)
    @Consumes(APPLICATION_JSON)
    @Counted(name="io.microprofile.showcase.vote.api.SessionVote.rateSession.monotonic.absolute(true)",monotonic=true,absolute=true,tags="app=vote")
    public SessionRating rateSession(SessionRating sessionRating) {
        String attendeeId = sessionRating.getAttendeeId();
        Attendee attendee = selectedAttendeeDAO.getAttendee(attendeeId);
        if (attendee == null) {
            throw new BadRequestException("Invalid attendee id: " + attendeeId);
        }

        SessionRating rating = selectedSessionRatingDAO.rateSession(sessionRating);
        return rating;
    }

    @GET
    @Path("/rate")
    @Produces(APPLICATION_JSON)
    @Counted(name="io.microprofile.showcase.vote.api.SessionVote.getAllSessionRatings.monotonic.absolute(true)",monotonic=true,absolute=true,tags="app=vote")
    public Collection<SessionRating> getAllSessionRatings() {
        return selectedSessionRatingDAO.getAllRatings();
    }

    @PUT
    @Path("/rate/{id}")
    @Produces(APPLICATION_JSON)
    @Consumes(APPLICATION_JSON)
    public SessionRating updateRating(@PathParam("id") String id, SessionRating newRating) {

        SessionRating original = selectedSessionRatingDAO.getRating(id);
        if (original == null) {
            throw new NotFoundException("Session Rating not found: " + id);
        }
        original.setSession(newRating.getSession());
        original.setRating(newRating.getRating());
        Attendee attendee = selectedAttendeeDAO.getAttendee(newRating.getAttendeeId());
        if (attendee == null) {
            throw new BadRequestException("Invalid attendee id: " + newRating.getAttendeeId());
        }
        original.setAttendee(attendee.getId());

        SessionRating updated = selectedSessionRatingDAO.updateRating(original);

        return updated;
    }

    @GET
    @Path("/rate/{id}")
    @Produces(APPLICATION_JSON)
    public SessionRating getRating(@PathParam("id") String id) {
        SessionRating rating = selectedSessionRatingDAO.getRating(id);
        if (rating == null) {
            throw new NotFoundException("Rating not found: " + id);
        }
        return rating;
    }

    @DELETE
    @Path("/rate/{id}")
    @Produces(APPLICATION_JSON)
    public void deleteRating(@PathParam("id") String id) {
        SessionRating rating = getRating(id);
        selectedSessionRatingDAO.deleteRating(rating.getId());
    }

    @GET
    @Path("/ratingsBySession")
    @Produces(APPLICATION_JSON)
    @Consumes(APPLICATION_JSON)
    @Counted(name="io.microprofile.showcase.vote.api.SessionVote.allSessionVotes.monotonic.absolute(true)",monotonic=true,absolute=true,tags="app=vote")
    public Collection<SessionRating> allSessionVotes(@QueryParam("sessionId") String sessionId) {
        return selectedSessionRatingDAO.getRatingsBySession(sessionId);
    }

    @GET
    @Path("/averageRatingBySession")
    @Produces(APPLICATION_JSON)
    @Consumes(APPLICATION_JSON)
    @Counted(name="io.microprofile.showcase.vote.api.SessionVote.sessionRatingAverage.monotonic.absolute(true)",monotonic=true,absolute=true,tags="app=vote")
    public double sessionRatingAverage(@QueryParam("sessionId") String sessionId) {
        Collection<SessionRating> allSessionVotes = allSessionVotes(sessionId);
        int denominator = allSessionVotes.size();
        int numerator = 0;
        for (SessionRating rating : allSessionVotes) {
            numerator += rating.getRating();
        }
        return ((double) numerator / denominator);
    }

    @GET
    @Path("/ratingsByAttendee")
    @Produces(APPLICATION_JSON)
    @Consumes(APPLICATION_JSON)
    @Counted(name="io.microprofile.showcase.vote.api.SessionVote.votesByAttendee.monotonic.absolute(true)",monotonic=true,absolute=true)
    public Collection<SessionRating> votesByAttendee(@QueryParam("attendeeId") String attendeeId) {
        Attendee attendee = selectedAttendeeDAO.getAttendee(attendeeId);
        if (attendee == null) {
            throw new BadRequestException("Invalid attendee id: " + attendeeId);
        }
        return selectedSessionRatingDAO.getRatingsByAttendee(attendeeId);
    }

    ///////////////////////////////////////////////////////////////////////////////
    // The following methods are intended for testing only - not part of the API //
    void clearAllAttendees() {
        selectedAttendeeDAO.clearAllAttendees();
    }

    void clearAllRatings() {
        selectedSessionRatingDAO.clearAllRatings();
    }
    
    @POST
    @Path("/updateHealthStatus")
    @Produces(TEXT_PLAIN)
    @Consumes(TEXT_PLAIN)
    @Counted(name="io.microprofile.showcase.vote.api.SessionVote.updateHealthStatus.monotonic.absolute(true)",monotonic=true,absolute=true,tags="app=vote")
    public void updateHealthStatus(@QueryParam("isAppDown") Boolean isAppDown) {
    	healthCheckBean.setIsAppDown(isAppDown);
    }

}
