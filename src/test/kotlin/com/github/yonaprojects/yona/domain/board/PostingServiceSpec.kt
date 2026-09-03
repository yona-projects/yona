package com.github.yonaprojects.yona.domain.board

import com.github.yonaprojects.yona.AbstractIntegrationTest
import com.github.yonaprojects.yona.domain.enumeration.EventType
import com.github.yonaprojects.yona.domain.enumeration.ResourceType
import com.github.yonaprojects.yona.domain.notification.NotificationEventRepository
import com.github.yonaprojects.yona.domain.notification.NotificationMailRepository
import com.github.yonaprojects.yona.domain.project.Project
import com.github.yonaprojects.yona.domain.project.ProjectRepository
import com.github.yonaprojects.yona.domain.project.ProjectScope
import com.github.yonaprojects.yona.domain.project.TitleHeadRepository
import com.github.yonaprojects.yona.domain.user.User
import com.github.yonaprojects.yona.domain.user.UserRepository
import com.github.yonaprojects.yona.domain.watch.Watch
import com.github.yonaprojects.yona.domain.watch.WatchRepository
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.assertions.throwables.shouldThrow
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.domain.PageRequest
import org.springframework.transaction.annotation.Transactional

// yona BoardApp.newPost/deletePost 대응 (P1-18): 게시글 생성/삭제 시 알림 미발송 문제 검증
@Transactional
class PostingServiceSpec @Autowired constructor(
    private val postingService: PostingService,
    private val postingRepository: PostingRepository,
    private val projectRepository: ProjectRepository,
    private val userRepository: UserRepository,
    private val notificationEventRepository: NotificationEventRepository,
    private val notificationMailRepository: NotificationMailRepository,
    private val watchRepository: WatchRepository,
    private val titleHeadRepository: TitleHeadRepository,
    private val postingCommentRepository: PostingCommentRepository
) : AbstractIntegrationTest() {

    init {
        describe("PostingService 알림 발행 테스트 (P1-18)") {
            // NotificationEventRecorder(P1-27)가 저장 시 NotificationMail 마커도 함께 만들므로
            // (OneToOne), notification_event를 지우기 전에 반드시 notification_mail을 먼저 지워야
            // FK/영속성 컨텍스트 불일치가 나지 않는다.
            fun resetNotifications() {
                notificationMailRepository.deleteAll()
                notificationEventRepository.deleteAll()
            }

            beforeEach {
                watchRepository.deleteAll()
                resetNotifications()
                postingCommentRepository.deleteAll()
                postingRepository.deleteAll()
                titleHeadRepository.deleteAll()
                projectRepository.deleteAll()
                userRepository.deleteAll()
            }

            it("게시글을 새로 작성하면 신규 게시글(NEW_POSTING) 알림 이벤트가 발행되어야 한다") {
                val author = userRepository.save(User(loginId = "writer", name = "작성자", email = "writer@yona.io"))
                val project = projectRepository.save(Project(name = "board-project", owner = "writer", projectScope = ProjectScope.PUBLIC))
                // NotificationEventRecorder(P1-27)는 legacy NotificationEvent.add()와 동일하게 수신자가
                // 없으면 저장하지 않으므로(receivers.isEmpty()), 작성자 본인 외에 실제로 알림을 받을
                // 프로젝트 감시자를 한 명 둔다.
                val watcher = userRepository.save(User(loginId = "watcher", name = "감시자", email = "watcher@yona.io"))
                watchRepository.save(Watch(user = watcher, resourceType = ResourceType.PROJECT, resourceId = project.id.toString()))

                val posting = Posting(
                    title = "공지사항입니다",
                    body = "본문입니다",
                    project = project
                )

                val saved = postingService.createPosting(project.id!!, posting, author.id!!)

                val events = notificationEventRepository.findAll()
                events.size shouldBe 1
                val event = events.first()
                event.eventType shouldBe EventType.NEW_POSTING
                event.resourceType shouldBe ResourceType.BOARD_POST
                event.resourceId shouldBe saved.id.toString()
                event.senderId shouldBe author.id
            }

            // yona NotificationEvent.forNewPost()의 getReceivers(abstractPosting, except)(watchers +
            // getMentionedUsers(body)) 대응 (P1-127). 신규 게시글 본문의 @멘션도 알림 수신자에
            // 포함되어야 한다.
            // publishNotification()의 notificationEventRecorder.record(notificationEvent)?.let { ... }는
            // record()가 receivers.isEmpty()일 때 null을 반환해(레거시와 동일) 저장하지 않는데, 기존
            // 테스트는 전부 워처를 하나 이상 둬서 이 null 분기가 비어 있었다.
            // yona controllers/api/BoardApi.java createPostingNode()의 "number 필드가 있으면
            // saveWithNumber(number), 없으면 save()" 대응 (P2-57 복원, legacy Open API 마이그레이션
            // 전용 옵션).
            it("explicitNumber가 주어지면 project.lastPostingNumber 카운터를 건드리지 않고 그 번호를 그대로 쓴다") {
                val author = userRepository.save(User(loginId = "import-writer", name = "임포트작성자", email = "import-writer@yona.io"))
                val project = projectRepository.save(Project(name = "import-board-project", owner = "import-writer", projectScope = ProjectScope.PUBLIC, lastPostingNumber = 5))

                val imported = postingService.createPosting(
                    project.id!!,
                    Posting(title = "과거 게시글 임포트", body = "본문", project = project),
                    author.id!!,
                    explicitNumber = 42L
                )

                imported.number shouldBe 42L
                projectRepository.findById(project.id!!).get().lastPostingNumber shouldBe 5L

                val next = postingService.createPosting(
                    project.id!!,
                    Posting(title = "다음 정상 게시글", body = "본문", project = project),
                    author.id!!
                )
                next.number shouldBe 6L
            }

            it("워처도 멘션도 없이 게시글을 작성하면 수신자가 없어 알림 이벤트가 저장되지 않아야 한다") {
                val author = userRepository.save(User(loginId = "lonely-writer", name = "작성자", email = "lonely-writer@yona.io"))
                val project = projectRepository.save(Project(name = "lonely-board-project", owner = "lonely-writer", projectScope = ProjectScope.PUBLIC))
                val posting = Posting(title = "아무도 안 보는 글", body = "본문", project = project)

                postingService.createPosting(project.id!!, posting, author.id!!)

                notificationEventRepository.findAll().size shouldBe 0
            }

            it("게시글 본문에 멘션이 포함되어 있으면 멘션된 사용자도 신규 게시글 알림 수신자에 포함되어야 한다") {
                val author = userRepository.save(User(loginId = "mention-writer", name = "작성자", email = "mention-writer@yona.io"))
                val mentioned = userRepository.save(User(loginId = "mentioned-reader", name = "멘션대상", email = "mentioned-reader@yona.io"))
                val project = projectRepository.save(Project(name = "mention-board-project", owner = "mention-writer", projectScope = ProjectScope.PUBLIC))
                val posting = Posting(title = "공지", body = "@mentioned-reader 님 확인 부탁드립니다.", project = project)

                postingService.createPosting(project.id!!, posting, author.id!!)

                val events = notificationEventRepository.findAll()
                events.size shouldBe 1
                events.first().receivers.map { it.loginId } shouldBe listOf("mentioned-reader")
            }

            it("본인이 작성한 글을 수정하고 알림 발송 옵션을 선택하지 않으면 알림이 발행되지 않아야 한다(P1-44)") {
                val author = userRepository.save(User(loginId = "writer3", name = "작성자3", email = "writer3@yona.io"))
                val project = projectRepository.save(Project(name = "board-project3", owner = "writer3"))
                val saved = postingService.createPosting(project.id!!, Posting(title = "원본", body = "원본 본문", project = project), author.id!!)
                resetNotifications()

                postingService.updatePosting(
                    projectId = project.id!!, number = saved.number!!,
                    title = "수정됨", body = "수정된 본문", notice = false, readme = false,
                    authorId = author.id!!, sendNotificationMail = false
                )

                notificationEventRepository.findAll().size shouldBe 0
            }

            it("본인이 작성한 글을 수정할 때 알림 발송 옵션을 선택하면 알림(POSTING_BODY_CHANGED)이 발행되어야 한다(P1-44)") {
                val author = userRepository.save(User(loginId = "writer4", name = "작성자4", email = "writer4@yona.io"))
                val project = projectRepository.save(Project(name = "board-project4", owner = "writer4", projectScope = ProjectScope.PUBLIC))
                val watcher = userRepository.save(User(loginId = "watcher4", name = "감시자4", email = "watcher4@yona.io"))
                watchRepository.save(Watch(user = watcher, resourceType = ResourceType.PROJECT, resourceId = project.id.toString()))
                val saved = postingService.createPosting(project.id!!, Posting(title = "원본", body = "원본 본문", project = project), author.id!!)
                resetNotifications()

                postingService.updatePosting(
                    projectId = project.id!!, number = saved.number!!,
                    title = "수정됨", body = "수정된 본문", notice = false, readme = false,
                    authorId = author.id!!, sendNotificationMail = true
                )

                val events = notificationEventRepository.findAll()
                events.size shouldBe 1
                events.first().eventType shouldBe EventType.POSTING_BODY_CHANGED
                events.first().oldValue shouldBe "원본 본문"
                events.first().newValue shouldBe "수정된 본문"
            }

            it("본문을 수정하면 변경 이력(history)이 기록되어야 한다(P2-02)") {
                val author = userRepository.save(User(loginId = "writer6", name = "작성자6", email = "writer6@yona.io"))
                val project = projectRepository.save(Project(name = "board-project6", owner = "writer6"))
                val saved = postingService.createPosting(project.id!!, Posting(title = "원본", body = "원본 본문", project = project), author.id!!)
                resetNotifications()

                val updated = postingService.updatePosting(
                    projectId = project.id!!, number = saved.number!!,
                    title = "수정됨", body = "수정된 본문", notice = false, readme = false,
                    authorId = author.id!!, sendNotificationMail = false
                )

                updated.history shouldNotBe null
                updated.history!!.contains("history-made-by") shouldBe true
                updated.history!!.contains("작성자6") shouldBe true
            }

            it("본문이 null인 게시글을 작성하고 수정하면 빈 문자열로 처리돼야 한다") {
                val author = userRepository.save(User(loginId = "writer-nullbody", name = "작성자", email = "writer-nullbody@yona.io"))
                val project = projectRepository.save(Project(name = "board-project-nullbody", owner = "writer-nullbody"))
                val saved = postingService.createPosting(project.id!!, Posting(title = "본문없음", body = null, project = project), author.id!!)
                resetNotifications()

                val updated = postingService.updatePosting(
                    projectId = project.id!!, number = saved.number!!,
                    title = "본문없음", body = "새 본문", notice = false, readme = false,
                    authorId = author.id!!, sendNotificationMail = false
                )

                updated.history shouldNotBe null
            }

            it("업데이트 알림 대상 수신자가 없으면 알림 이벤트가 저장되지 않아야 한다") {
                val author = userRepository.save(User(loginId = "writer-noreceiver", name = "작성자", email = "writer-noreceiver@yona.io"))
                val editor = userRepository.save(User(loginId = "editor-noreceiver", name = "편집자", email = "editor-noreceiver@yona.io"))
                val project = projectRepository.save(Project(name = "board-project-noreceiver", owner = "writer-noreceiver"))
                val saved = postingService.createPosting(project.id!!, Posting(title = "원본", body = "원본 본문", project = project), author.id!!)
                resetNotifications()

                postingService.updatePosting(
                    projectId = project.id!!, number = saved.number!!,
                    title = "수정됨", body = "수정된 본문", notice = false, readme = false,
                    authorId = editor.id!!, sendNotificationMail = false
                )

                notificationEventRepository.findAll().size shouldBe 0
            }

            it("본문이 바뀌지 않으면 history를 기록하지 않아야 한다(P2-02)") {
                val author = userRepository.save(User(loginId = "writer7", name = "작성자7", email = "writer7@yona.io"))
                val project = projectRepository.save(Project(name = "board-project7", owner = "writer7"))
                val saved = postingService.createPosting(project.id!!, Posting(title = "원본", body = "동일 본문", project = project), author.id!!)
                resetNotifications()

                val updated = postingService.updatePosting(
                    projectId = project.id!!, number = saved.number!!,
                    title = "제목만 수정", body = "동일 본문", notice = false, readme = false,
                    authorId = author.id!!, sendNotificationMail = false
                )

                updated.history shouldBe null
            }

            // yona BoardApp.editPost()의 unmarkAnotherReadmePostingIfExists() 대응(#146 재검토,
            // TASK-0263) — 다른 글을 readme=true로 수정하면 같은 프로젝트에서 기존에 readme였던
            // 글은 자동으로 해제되어야 한다(프로젝트당 README 글은 항상 최대 1개).
            it("readme=true로 수정하면 같은 프로젝트의 기존 readme 글은 자동으로 해제되어야 한다(#146)") {
                val author = userRepository.save(User(loginId = "writer8", name = "작성자8", email = "writer8@yona.io"))
                val project = projectRepository.save(Project(name = "board-project8", owner = "writer8"))
                val oldReadme = postingService.createPosting(
                    project.id!!, Posting(title = "예전 README", body = "예전 내용", readme = true, project = project), author.id!!
                )
                val newPost = postingService.createPosting(
                    project.id!!, Posting(title = "새 글", body = "새 내용", project = project), author.id!!
                )
                resetNotifications()

                postingService.updatePosting(
                    projectId = project.id!!, number = newPost.number!!,
                    title = "새 글", body = "새 내용", notice = false, readme = true,
                    authorId = author.id!!, sendNotificationMail = false
                )

                val refreshedOld = postingService.getPosting(project.id!!, oldReadme.number!!)!!
                val refreshedNew = postingService.getPosting(project.id!!, newPost.number!!)!!
                refreshedOld.readme shouldBe false
                refreshedNew.readme shouldBe true
            }

            it("본인이 작성하지 않은 글을 수정하면 알림 발송 옵션과 무관하게 항상 알림이 발행되어야 한다(P1-44)") {
                val author = userRepository.save(User(loginId = "writer5", name = "작성자5", email = "writer5@yona.io"))
                val editor = userRepository.save(User(loginId = "editor1", name = "편집자", email = "editor1@yona.io"))
                val project = projectRepository.save(Project(name = "board-project5", owner = "writer5", projectScope = ProjectScope.PUBLIC))
                // 편집자(=수신자에서 제외되는 본인)가 아닌 실제 수신자가 될 프로젝트 감시자를 둔다.
                val watcher = userRepository.save(User(loginId = "watcher5", name = "감시자5", email = "watcher5@yona.io"))
                watchRepository.save(Watch(user = watcher, resourceType = ResourceType.PROJECT, resourceId = project.id.toString()))
                val saved = postingService.createPosting(project.id!!, Posting(title = "원본", body = "원본 본문", project = project), author.id!!)
                resetNotifications()

                postingService.updatePosting(
                    projectId = project.id!!, number = saved.number!!,
                    title = "수정됨", body = "수정된 본문", notice = false, readme = false,
                    authorId = editor.id!!, sendNotificationMail = false
                )

                val events = notificationEventRepository.findAll()
                events.size shouldBe 1
                events.first().eventType shouldBe EventType.POSTING_BODY_CHANGED
                events.first().senderId shouldBe editor.id
            }

            it("게시글을 삭제하면 삭제(RESOURCE_DELETED) 알림 이벤트가 발행되어야 한다") {
                val author = userRepository.save(User(loginId = "writer2", name = "작성자2", email = "writer2@yona.io"))
                val project = projectRepository.save(Project(name = "board-project2", owner = "writer2", projectScope = ProjectScope.PUBLIC))
                val watcher = userRepository.save(User(loginId = "watcher2", name = "감시자2", email = "watcher2@yona.io"))
                watchRepository.save(Watch(user = watcher, resourceType = ResourceType.PROJECT, resourceId = project.id.toString()))

                val posting = Posting(title = "삭제될 글", body = "본문", project = project)
                val saved = postingService.createPosting(project.id!!, posting, author.id!!)
                resetNotifications()

                postingService.deletePosting(project.id!!, saved.number!!, author.id!!)

                val events = notificationEventRepository.findAll()
                events.size shouldBe 1
                val event = events.first()
                event.eventType shouldBe EventType.RESOURCE_DELETED
                event.resourceType shouldBe ResourceType.BOARD_POST
                event.resourceId shouldBe saved.id.toString()
                event.senderId shouldBe author.id
            }

            // yona Project.delete() 게시글 삭제 대응(P0-19 조사 중 발견 — Issue 쪽과 동일한 결함).
            // PostingComment.posting FK가 nullable=false라 댓글이 달린 게시글은 postingRepository
            // .delete(posting) 단독 호출 시 FK 위반으로 실패했다.
            it("댓글이 달린 게시글도 FK 위반 없이 삭제되고 댓글도 함께 정리되어야 한다") {
                val author = userRepository.save(User(loginId = "writer-cc", name = "작성자CC", email = "writer-cc@yona.io"))
                val project = projectRepository.save(Project(name = "board-project-cc", owner = "writer-cc", projectScope = ProjectScope.PUBLIC))
                val saved = postingService.createPosting(project.id!!, Posting(title = "댓글 달릴 글", body = "본문", project = project), author.id!!)
                postingCommentRepository.save(
                    PostingComment(
                        contents = "댓글", authorId = author.id, authorLoginId = author.loginId,
                        authorName = author.name, projectId = project.id, posting = saved
                    )
                )

                postingService.deletePosting(project.id!!, saved.number!!, author.id!!)

                postingRepository.findById(saved.id!!).isPresent shouldBe false
                postingCommentRepository.findByPostingIdOrderByCreatedDateAsc(saved.id!!) shouldBe emptyList()
            }

            // yona AbstractPosting.save()/AbstractPostingApp.editPosting()/AbstractPosting.delete()의
            // TitleHead 연동 대응 (P1-103).
            describe("createPosting/updatePosting/deletePosting의 TitleHead(제목 머리말 자동완성) 연동") {
                it("createPosting으로 대괄호 머리말이 있는 제목을 만들면 TitleHead가 빈도 1로 저장되어야 한다") {
                    val author = userRepository.save(User(loginId = "th-writer1", name = "작성자", email = "th-w1@yona.io"))
                    val project = projectRepository.save(Project(name = "th-board1", owner = "th-writer1", projectScope = ProjectScope.PUBLIC))

                    postingService.createPosting(project.id!!, Posting(title = "[공지] 점검 안내", body = "본문", project = project), author.id!!)

                    val found = titleHeadRepository.findByProjectIdAndHeadKeyword(project.id!!, "공지")
                    found shouldNotBe null
                    found!!.frequency shouldBe 1
                }

                it("updatePosting으로 제목을 바꾸면 새 머리말은 생기고 예전 머리말은 사라져야 한다") {
                    val author = userRepository.save(User(loginId = "th-writer2", name = "작성자2", email = "th-w2@yona.io"))
                    val project = projectRepository.save(Project(name = "th-board2", owner = "th-writer2", projectScope = ProjectScope.PUBLIC))
                    val saved = postingService.createPosting(project.id!!, Posting(title = "[공지] 점검 안내", body = "본문", project = project), author.id!!)

                    postingService.updatePosting(
                        projectId = project.id!!, number = saved.number!!,
                        title = "[안내] 새 소식", body = "본문", notice = false, readme = false,
                        authorId = author.id!!, sendNotificationMail = false
                    )

                    titleHeadRepository.findByProjectIdAndHeadKeyword(project.id!!, "공지") shouldBe null
                    titleHeadRepository.findByProjectIdAndHeadKeyword(project.id!!, "안내")!!.frequency shouldBe 1
                }

                it("deletePosting으로 게시글을 지우면 TitleHead 빈도가 0이 되어 행이 삭제되어야 한다") {
                    val author = userRepository.save(User(loginId = "th-writer3", name = "작성자3", email = "th-w3@yona.io"))
                    val project = projectRepository.save(Project(name = "th-board3", owner = "th-writer3", projectScope = ProjectScope.PUBLIC))
                    val saved = postingService.createPosting(project.id!!, Posting(title = "[공지] 삭제될 글", body = "본문", project = project), author.id!!)

                    postingService.deletePosting(project.id!!, saved.number!!, author.id!!)

                    titleHeadRepository.findByProjectIdAndHeadKeyword(project.id!!, "공지") shouldBe null
                }
            }

            describe("예외 및 엣지 케이스 테스트 (미커버 분기)") {
                it("getPostings - 정상적으로 프로젝트의 게시글 페이지를 반환해야 한다") {
                    val author = userRepository.save(User(loginId = "gp-writer", name = "작성자", email = "gp-writer@yona.io"))
                    val project = projectRepository.save(Project(name = "gp-project", owner = "gp-writer"))
                    postingService.createPosting(project.id!!, Posting(title = "글1", body = "본문1", project = project), author.id!!)

                    val page = postingService.getPostings(project.id!!, PageRequest.of(0, 10))

                    page.totalElements shouldBe 1
                }

                it("getNotices - 정상적으로 프로젝트의 공지 게시글 목록을 반환해야 한다") {
                    val author = userRepository.save(User(loginId = "gn-writer", name = "작성자", email = "gn-writer@yona.io"))
                    val project = projectRepository.save(Project(name = "gn-project", owner = "gn-writer"))
                    postingService.createPosting(project.id!!, Posting(title = "공지글", body = "본문", notice = true, project = project), author.id!!)
                    postingService.createPosting(project.id!!, Posting(title = "일반글", body = "본문", project = project), author.id!!)

                    val notices = postingService.getNotices(project.id!!)

                    notices.size shouldBe 1
                    notices.first().title shouldBe "공지글"
                }

                it("getPostings - 존재하지 않는 프로젝트 조회 시 예외 발생") {
                    shouldThrow<IllegalArgumentException> {
                        postingService.getPostings(9999L, PageRequest.of(0, 10))
                    }.message shouldBe "프로젝트를 찾을 수 없습니다."
                }

                it("getNotices - 존재하지 않는 프로젝트 조회 시 예외 발생") {
                    shouldThrow<IllegalArgumentException> {
                        postingService.getNotices(9999L)
                    }.message shouldBe "프로젝트를 찾을 수 없습니다."
                }

                it("getPosting - 존재하지 않는 프로젝트 조회 시 예외 발생") {
                    shouldThrow<IllegalArgumentException> {
                        postingService.getPosting(9999L, 1L)
                    }.message shouldBe "프로젝트를 찾을 수 없습니다."
                }

                it("createPosting - 존재하지 않는 프로젝트 조회 시 예외 발생") {
                    val dummyProject = Project(name = "dummy", owner = "dummy")
                    val posting = Posting(title = "test", body = "test", project = dummyProject)
                    shouldThrow<IllegalArgumentException> {
                        postingService.createPosting(9999L, posting, 1L)
                    }.message shouldBe "프로젝트를 찾을 수 없습니다."
                }

                it("createPosting - 존재하지 않는 사용자 조회 시 예외 발생") {
                    val project = projectRepository.save(Project(name = "test-project", owner = "owner"))
                    val posting = Posting(title = "test", body = "test", project = project)
                    shouldThrow<IllegalArgumentException> {
                        postingService.createPosting(project.id!!, posting, 9999L)
                    }.message shouldBe "사용자를 찾을 수 없습니다."
                }

                it("updatePosting - 존재하지 않는 포스팅 조회 시 예외 발생") {
                    val project = projectRepository.save(Project(name = "test-project", owner = "owner"))
                    shouldThrow<IllegalArgumentException> {
                        postingService.updatePosting(project.id!!, 9999L, "title", "body", false, false, 1L, false)
                    }.message shouldBe "포스팅을 찾을 수 없습니다."
                }

                it("updatePosting - updater가 null인 경우(사용자가 삭제된 경우)에도 정상 수정되어야 한다") {
                    val author = userRepository.save(User(loginId = "author", name = "작성자", email = "author@yona.io"))
                    val project = projectRepository.save(Project(name = "test-project", owner = "author"))
                    val saved = postingService.createPosting(project.id!!, Posting(title = "원본", body = "본문", project = project), author.id!!)
                    
                    // authorId에 해당하는 사용자가 존재하지 않는 9999L로 전달
                    val updated = postingService.updatePosting(
                        projectId = project.id!!, number = saved.number!!,
                        title = "수정됨", body = "수정된 본문", notice = false, readme = true,
                        authorId = 9999L, sendNotificationMail = true
                    )

                    updated.title shouldBe "수정됨"
                    updated.history shouldBe null // updater가 없으므로 history 기록 불가
                }

                it("deletePosting - 존재하지 않는 포스팅 삭제 시 예외 발생") {
                    val project = projectRepository.save(Project(name = "test-project", owner = "owner"))
                    shouldThrow<IllegalArgumentException> {
                        postingService.deletePosting(project.id!!, 9999L, 1L)
                    }.message shouldBe "포스팅을 찾을 수 없습니다."
                }

                it("deletePosting - 존재하지 않는 사용자(actor) 삭제 시 예외 발생") {
                    val author = userRepository.save(User(loginId = "author2", name = "작성자2", email = "a2@yona.io"))
                    val project = projectRepository.save(Project(name = "test-project2", owner = "author2"))
                    val saved = postingService.createPosting(project.id!!, Posting(title = "원본", body = "본문", project = project), author.id!!)
                    
                    shouldThrow<IllegalArgumentException> {
                        postingService.deletePosting(project.id!!, saved.number!!, 9999L)
                    }.message shouldBe "사용자를 찾을 수 없습니다."
                }
            }
        }
    }
}
