/**
 * 소셜 공개 계약. {@code api.query}: 맞팔로우 Query(FriendshipQuery) · {@code api.web}: 친구·피드·랭킹·비교 컨트롤러. 공개 이벤트는
 * 없다(팔로우 시작·끝을 구독하는 컨텍스트가 없어 FollowStarted/FollowEnded 는 만들지 않았다).
 */
package com.kobi.territory.social.api;
