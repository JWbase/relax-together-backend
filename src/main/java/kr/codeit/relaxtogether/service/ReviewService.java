package kr.codeit.relaxtogether.service;

import static kr.codeit.relaxtogether.exception.ErrorCode.CANNOT_WRITE_REVIEW_AS_ORGANIZER;
import static kr.codeit.relaxtogether.exception.ErrorCode.GATHERING_NOT_FOUND;
import static kr.codeit.relaxtogether.exception.ErrorCode.USER_NOT_FOUND;

import kr.codeit.relaxtogether.dto.PagedResponse;
import kr.codeit.relaxtogether.dto.review.request.ReviewSearchCondition;
import kr.codeit.relaxtogether.dto.review.request.WriteReviewRequest;
import kr.codeit.relaxtogether.dto.review.response.GatheringReviewsResponse;
import kr.codeit.relaxtogether.dto.review.response.ReviewDetailsResponse;
import kr.codeit.relaxtogether.dto.review.response.ReviewScoreCountResponse;
import kr.codeit.relaxtogether.entity.Review;
import kr.codeit.relaxtogether.entity.User;
import kr.codeit.relaxtogether.entity.gathering.Gathering;
import kr.codeit.relaxtogether.exception.ApiException;
import kr.codeit.relaxtogether.repository.UserRepository;
import kr.codeit.relaxtogether.repository.gathering.GatheringRepository;
import kr.codeit.relaxtogether.repository.review.ReviewRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Transactional(readOnly = true)
@Service
public class ReviewService {

    private final ReviewRepository reviewRepository;
    private final UserRepository userRepository;
    private final GatheringRepository gatheringRepository;

    @Transactional
    public void writeReview(WriteReviewRequest writeReviewRequest, String loginEmail) {
        User user = userRepository.findByEmail(loginEmail)
            .orElseThrow(() -> new ApiException(USER_NOT_FOUND));
        Gathering gathering = gatheringRepository.findById(writeReviewRequest.getGatheringId())
            .orElseThrow(() -> new ApiException(GATHERING_NOT_FOUND));
        if (gathering.getHostUser().getEmail().equals(loginEmail)) {
            throw new ApiException(CANNOT_WRITE_REVIEW_AS_ORGANIZER);
        }
        Review review = writeReviewRequest.toEntity(user, gathering);
        reviewRepository.save(review);
    }

    public PagedResponse<ReviewDetailsResponse> getLoginUserReviews(String loginEmail, Pageable pageable) {
        User user = userRepository.findByEmail(loginEmail)
            .orElseThrow(() -> new ApiException(USER_NOT_FOUND));
        Slice<ReviewDetailsResponse> reviewsByUserId = reviewRepository.findReviewsByUserId(user.getId(), pageable);
        return new PagedResponse<>(
            reviewsByUserId.getContent(),
            reviewsByUserId.hasNext(),
            reviewsByUserId.getNumberOfElements()
        );
    }

    public GatheringReviewsResponse getReviewsByGatheringId(Long gatheringId, Pageable pageable) {
        return reviewRepository.findReviewsByGatheringId(gatheringId, pageable);
    }

    public PagedResponse<ReviewDetailsResponse> getReviewsByConditions(ReviewSearchCondition reviewSearchCondition,
        Pageable pageable) {
        Slice<ReviewDetailsResponse> reviewsByConditions = reviewRepository.findReviewsByConditions(
            reviewSearchCondition, pageable);
        return new PagedResponse<>(
            reviewsByConditions.getContent(),
            reviewsByConditions.hasNext(),
            reviewsByConditions.getNumberOfElements()
        );
    }

    public ReviewScoreCountResponse getReviewScoreCounts(String type, String typeDetail) {
        return reviewRepository.findReviewScoreCounts(type, typeDetail);
    }
}
