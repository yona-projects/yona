package controllers;

import java.io.*;
import java.util.*;

import models.*;

import org.codehaus.jackson.node.ObjectNode;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.*;
import org.eclipse.jgit.errors.*;
import org.eclipse.jgit.lib.*;
import org.eclipse.jgit.revwalk.*;
import org.eclipse.jgit.transport.*;
import org.eclipse.jgit.transport.RefAdvertiser.PacketLineOutRefAdvertiser;
import org.eclipse.jgit.treewalk.*;
import org.eclipse.jgit.treewalk.filter.*;

import play.Logger;
import play.libs.Json;
import play.mvc.*;

import views.html.code.*;

public class GitApp extends Controller {
    public static final String REPO_PREFIX = "repo/git/";

    public static Result advertise(String projectName, String service) throws IOException {
        Logger.debug("GitApp.advertise : " + request().toString());

        response().setContentType("application/x-" + service + "-advertisement");

        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        PacketLineOut out = new PacketLineOut(byteArrayOutputStream);
        out.writeString("# service=" + service + "\n");
        out.end();

        Repository repository = new RepositoryBuilder().setGitDir(
                new File(REPO_PREFIX + projectName + ".git")).build();
        if (service.equals("git-upload-pack")) {
            UploadPack uploadPack = new UploadPack(repository);

            uploadPack.setBiDirectionalPipe(false);
            uploadPack.sendAdvertisedRefs(new PacketLineOutRefAdvertiser(out));
        } else if (service.equals("git-receive-pack")) {
            ReceivePack receivePack = new ReceivePack(repository);

            receivePack.sendAdvertisedRefs(new PacketLineOutRefAdvertiser(out));
        } else {
            return forbidden("Unsuppoted service: '" + service + "'");
        }

        return ok(byteArrayOutputStream.toByteArray());
    }

    public static Result serviceRpc(String projectName, String service) throws IOException {
        Logger.debug("GitApp.advertise : " + request().toString());

        response().setContentType("application/x-" + service + "-result");

        Repository repository = new RepositoryBuilder().setGitDir(
                new File(REPO_PREFIX + projectName + ".git")).build();

        // receivePack.setEchoCommandFailures(true);//git버전에 따라서 불린값 설정필요.

        // FIXME 스트림으로..
        byte[] buf = request().body().asRaw().asBytes();
        ByteArrayInputStream in = new ByteArrayInputStream(buf);

        ByteArrayOutputStream out = new ByteArrayOutputStream();

        if (service.equals("git-upload-pack")) {
            UploadPack uploadPack = new UploadPack(repository);

            uploadPack.setBiDirectionalPipe(false);
            uploadPack.upload(in, out, null);

        } else if (service.equals("git-receive-pack")) {
            ReceivePack receivePack = new ReceivePack(repository);

            receivePack.setBiDirectionalPipe(false);
            receivePack.receive(in, out, null);
        } else {
            return forbidden("Unsuppoted service: '" + service + "'");
        }
        out.close();
        return ok(out.toByteArray());
    }

    public static Result getFile(String projectName, String path) throws IOException {
        Repository repository = new RepositoryBuilder().setGitDir(
                new File(REPO_PREFIX + projectName + ".git")).build();
        // 현재 HEAD의 파일 및 디렉토리 얻어오기(그냥 정보얻어오기.. ) repository.getDirectory()
        //
        // 커밋 로그 불러오기
        // ObjectReader reader = repository.newObjectReader();
        // reader.open(repository.getRef("HEAD").getObjectId()).getBytes()
        // repository.resolve("5c72680162f20c1c")

        // RevTree tree = new
        // RevWalk(repository).parseTree(repository.resolve("5c72680162f20c1c"));
        // TreeWalk treeWalk = new TreeWalk(repository);
        // treeWalk.addTree(tree);

        // repository.open(treeWalk.getObjectId(0)).getBytes()

        return ok();
    }

    public static Result viewCode(String projectName) {
        return ok(gitView.render("http://localhost:9000/" + projectName,
                Project.findByName(projectName)));
    }

    public static Result ajaxRequest(String projectName, String path) throws IOException, NoHeadException, GitAPIException {
        Repository repository = new RepositoryBuilder().setGitDir(
                new File(REPO_PREFIX + projectName + ".git")).build();
        RevTree tree = new RevWalk(repository).parseTree(repository.resolve("HEAD"));
        TreeWalk treeWalk = new TreeWalk(repository);
        treeWalk.addTree(tree);
        

        // XXX 수많은 리팩토링이 필요함.
        if (path.equals("")) {
            return listingDirectory(treeWalk, repository);
        } else {
            PathFilter filter = PathFilter.create(path);
            treeWalk.setFilter(filter);

            while (treeWalk.next()) {
                if (filter.isDone(treeWalk)) {
                    break;
                } else if (treeWalk.isSubtree()) {
                    treeWalk.enterSubtree();
                }
            }
        }

        if (treeWalk.isSubtree()) {
            treeWalk.enterSubtree();
            return listingDirectory(treeWalk, repository);
            
        } else {
            // FIXME 파일 타잎을 추론해서 내려줘야 함.
            // 대부분의 경우에는 text로 내려주되 이미지나 동영상 같은 경우에는 알맞은 걸로 내려준다.
            //RevCommit
            ObjectId objectId = treeWalk.getObjectId(0);
            
            Git git = new Git(repository);
            Iterator<RevCommit> commits = git.log().addPath(path).call().iterator();
            RevCommit commit = commits.next();
            
            ObjectNode result = Json.newObject();
            result.put("commitMessage", commit.getShortMessage());
            result.put("commiter", commit.getAuthorIdent().getName());
            // TODO 날짜 계산해서 넣어야함. 날짜가 부정확함.
            result.put("commitDate", new Date(commit.getCommitTime()).toString());
            
            
            String str = new String(repository.open(objectId).getBytes());
            result.put("data", str);
            
            return ok(result);
        }
    }

    private static Result listingDirectory(TreeWalk treeWalk, Repository repository) throws MissingObjectException,
            IncorrectObjectTypeException, CorruptObjectException, IOException, NoHeadException, GitAPIException {
        
        Git git = new Git(repository);
        
        // JSON으로 응답내려주기
        ObjectNode result = Json.newObject();
        while (treeWalk.next()) {
            RevCommit commit = git.log().addPath(treeWalk.getPathString()).call().iterator().next();
            
            ObjectNode data = Json.newObject();
            data.put("type", treeWalk.isSubtree() ? "folder" : "file");
            data.put("commitMessage", commit.getShortMessage());
            data.put("commiter", commit.getAuthorIdent().getName());
            data.put("commitDate", new Date(commit.getCommitTime()).toString());
            result.put(treeWalk.getNameString(), data);
        }
        return ok(result);
    }
}
