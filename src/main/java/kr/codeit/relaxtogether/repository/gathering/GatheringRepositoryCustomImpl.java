package kr.codeit.relaxtogether.repository.gathering;

import com.querydsl.core.types.Order;
import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.CaseBuilder;
import com.querydsl.jpa.impl.JPAQueryFactory;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;
import kr.codeit.relaxtogether.dto.gathering.request.GatheringSearchCondition;
import kr.codeit.relaxtogether.dto.gathering.response.HostedGatheringResponse;
import kr.codeit.relaxtogether.dto.gathering.response.QHostedGatheringResponse;
import kr.codeit.relaxtogether.dto.gathering.response.QSearchGatheringResponse;
import kr.codeit.relaxtogether.dto.gathering.response.SearchGatheringResponse;
import kr.codeit.relaxtogether.entity.gathering.Location;
import kr.codeit.relaxtogether.entity.gathering.QGathering;
import kr.codeit.relaxtogether.entity.gathering.QUserGathering;
import kr.codeit.relaxtogether.entity.gathering.Status;
import kr.codeit.relaxtogether.entity.gathering.Type;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.SliceImpl;
import org.springframework.data.domain.Sort;

@RequiredArgsConstructor
public class GatheringRepositoryCustomImpl implements GatheringRepositoryCustom {

    private final JPAQueryFactory queryFactory;

    @Override
    public Slice<SearchGatheringResponse> searchGatherings(GatheringSearchCondition condition, Pageable pageable) {
        QGathering gathering = QGathering.gathering;
        QUserGathering userGathering = QUserGathering.userGathering;
        ZonedDateTime now = ZonedDateTime.now(ZoneOffset.UTC);

        List<SearchGatheringResponse> results = queryFactory
            .select(new QSearchGatheringResponse(
                gathering.id,
                gathering.type,
                gathering.name,
                gathering.dateTime,
                gathering.registrationEnd,
                gathering.location,
                userGathering.id.count(),
                gathering.capacity,
                gathering.imageUrl,
                gathering.hostUser.id
            ))
            .from(gathering)
            .leftJoin(userGathering).on(userGathering.gathering.id.eq(gathering.id))
            .where(
                isOngoing(),
                categoryEq(condition.getType()),
                locationEq(condition.getLocation()),
                dateBetween(condition.getDate()),
                hostUserEq(condition.getHostUser())
            )
            .groupBy(
                gathering.id, gathering.type, gathering.name, gathering.dateTime,
                gathering.registrationEnd, gathering.location, gathering.capacity,
                gathering.imageUrl, gathering.hostUser.id
            )
            .orderBy(
                new CaseBuilder()
                    .when(gathering.dateTime.before(now)).then(1)
                    .otherwise(0).asc(),
                applySorting(pageable.getSort(), gathering, userGathering),
                gathering.dateTime.asc())
            .offset(pageable.getOffset())
            .limit(pageable.getPageSize())
            .fetch();

        return new SliceImpl<>(results, pageable, results.size() == pageable.getPageSize());
    }

    @Override
    public Slice<HostedGatheringResponse> findGatheringsWithParticipantCountByHostUserId(Long hostUserId,
        Pageable pageable) {
        QGathering gathering = QGathering.gathering;
        QUserGathering userGathering = QUserGathering.userGathering;

        List<HostedGatheringResponse> results = queryFactory
            .select(new QHostedGatheringResponse(
                gathering.id,
                gathering.type,
                gathering.name,
                gathering.dateTime,
                gathering.registrationEnd,
                gathering.location,
                userGathering.id.count(),
                gathering.capacity,
                gathering.imageUrl,
                gathering.hostUser.id
            ))
            .from(gathering)
            .leftJoin(userGathering).on(userGathering.gathering.id.eq(gathering.id))
            .where(
                gathering.hostUser.id.eq(hostUserId),
                isOngoing()
            )
            .groupBy(
                gathering.id, gathering.type, gathering.name, gathering.dateTime,
                gathering.registrationEnd, gathering.location, gathering.capacity,
                gathering.imageUrl, gathering.hostUser.id
            )
            .orderBy(gathering.createdDate.desc())
            .offset(pageable.getOffset())
            .limit(pageable.getPageSize())
            .fetch();

        return new SliceImpl<>(results, pageable, results.size() == pageable.getPageSize());
    }

    private OrderSpecifier<?> applySorting(Sort sort, QGathering gathering, QUserGathering userGathering) {
        for (Sort.Order order : sort) {
            if (order.getProperty().equals("registrationEnd")) {
                return new OrderSpecifier<>(order.isAscending() ? Order.ASC : Order.DESC, gathering.registrationEnd);
            } else if (order.getProperty().equals("participantCount")) {
                return new OrderSpecifier<>(order.isAscending() ? Order.ASC : Order.DESC, userGathering.id.count());
            }
        }
        return new OrderSpecifier<>(Order.ASC, gathering.registrationEnd);
    }

    private BooleanExpression isOngoing() {
        return QGathering.gathering.status.eq(Status.ONGOING);
    }

    private BooleanExpression categoryEq(String category) {
        if (Type.MINDFULNESS.getParentCategory().equalsIgnoreCase(category)) {
            return QGathering.gathering.type.in(Type.OFFICE_STRETCHING, Type.MINDFULNESS);
        }
        return category == null ? null : QGathering.gathering.type.eq(Type.fromText(category));
    }

    private BooleanExpression locationEq(String locationText) {
        if (locationText == null || locationText.isEmpty()) {
            return null;
        }
        try {
            Location location = Location.fromText(locationText);
            return QGathering.gathering.location.eq(location);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private BooleanExpression dateBetween(ZonedDateTime date) {
        if (date == null) {
            return null;
        }
        ZonedDateTime startOfDay = date.toLocalDate().atStartOfDay(date.getZone());
        ZonedDateTime endOfDay = date.plusDays(1);
        return QGathering.gathering.dateTime.between(startOfDay, endOfDay);
    }

    private BooleanExpression hostUserEq(Long hostUser) {
        return hostUser == null ? null : QGathering.gathering.hostUser.id.eq(hostUser);
    }
}
