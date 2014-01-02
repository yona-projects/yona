/**
 * Yobi, Project Hosting SW
 *
 * Copyright 2013 NAVER Corp.
 * http://yobi.io
 *
 * @Author Wansoon Park
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package actors;

import java.util.List;

import models.PullRequest;
import models.PullRequestEventMessage;

/**
 * 변경된 branch 와 관련있는 모든 pullRequest 에 대해 병합을 시도하고 결과를 저장한다.
 */
public class RelatedPullRequestMergingActor extends PullRequestActor {
    @Override
    public void onReceive(Object object) {
        if (!(object instanceof PullRequestEventMessage)) {
            return;
        }

        PullRequestEventMessage message = (PullRequestEventMessage) object;
        List<PullRequest> pullRequests = PullRequest.findRelatedPullRequests(
                message.getProject(), message.getBranch());

        changeStateToMerging(pullRequests);
        processPullRequests(message, pullRequests);

    }

    /**
     * project/branch와 연관된 보낸코드들의 상태를 병합중으로 수정한다.
     * @param pullRequests
     */
    private void changeStateToMerging(List<PullRequest> pullRequests) {
        for (PullRequest pullRequest : pullRequests) {
            pullRequest.startMerge();
            pullRequest.update();
        }
    }

    private void processPullRequests(PullRequestEventMessage message, List<PullRequest> pullRequests) {
        for (PullRequest pullRequest : pullRequests) {
            processPullRequestMerging(message, pullRequest);
        }
    }
}
