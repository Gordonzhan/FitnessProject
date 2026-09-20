package com.fitness.mapper;

import com.fitness.entity.ImageUploadTicket;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface ImageUploadTicketMapper {
    int insert(ImageUploadTicket ticket);

    ImageUploadTicket findById(@Param("ticketId") String ticketId);

    int claim(@Param("ticketId") String ticketId,
              @Param("userId") Long userId,
              @Param("now") LocalDateTime now);

    int markConfirmed(@Param("ticketId") String ticketId,
                      @Param("confirmedAt") LocalDateTime confirmedAt);

    int markFailed(@Param("ticketId") String ticketId);

    int markExpired(@Param("ticketId") String ticketId,
                    @Param("userId") Long userId);

    List<ImageUploadTicket> findExpiredIssued(@Param("now") LocalDateTime now,
                                               @Param("limit") int limit);
}
