# fdroiddata !46150 follow up

https://gitlab.com/fdroid/fdroiddata/-/merge_requests/46150

## Why the rebase is not worth doing

The merge request page says: *"The source branch is 3931 commits behind the
target branch. Source branch will be rebased without pipeline."* fdroiddata
rebases on merge, so being behind master does not block anything, and a manual
rebase would not trigger CI either, because the pipeline cannot run on this
account at all. It would be motion without a result. Skipping it.

## What is actually stuck

All four pipeline runs ended in `error`, not a failed job. That is GitLab
declining shared runners to an unverified account, which is the verification
the contribution template says not to submit. duckniii anticipated this at the
time ("no issues, just commit the changes in yaml let it ask and fail").

Everything the reviewers asked for was pushed on 23 Aug: full commit hash
pinned, Summary dropped, Binaries and AllowedAPKSigningKeys added, and the
fastlane `images/` folder with icon and screenshots created. The
`waiting-on-response` label was applied before that and was never cleared, so
to anyone triaging the queue the merge request still looks like it is waiting
on the contributor.

## Comment to post

Post as Arcdub on the merge request.

> Checking in on this one. Everything asked for in review was pushed on 23 Aug:
> the build is pinned to full commit hash
> `81641298b4c9d3b2bd67245e62e0ad96e22b143a`, Summary is gone, Binaries and
> AllowedAPKSigningKeys are in, and `fastlane/metadata/android/en-US/images/`
> with `icon.png` and `phoneScreenshots/` is in the app repository.
>
> The merge request is still labelled `waiting-on-response`, which I think is
> left over from before that push rather than a question I have missed. If
> anything is still outstanding on my side I am glad to do it; otherwise could
> the label be cleared so this can go back into the review queue?
>
> On the red pipelines: all four ended in `error` rather than failing a job.
> This is a new GitLab account, so shared runners are refused until identity
> verification, which the contribution template asks contributors not to
> submit. That is why the runs never started, and it is not something I can fix
> from my side.
>
> Two small repository changes since the review, neither affecting the pinned
> build commit: `LICENSE` is now the verbatim MIT text, so GitHub detects it as
> MIT instead of reporting no assertion, and the bundled translation licences
> moved into `NOTICE.md`, where the two CC BY-SA 4.0 texts are now stated
> properly instead of everything being described as public domain.
>
> Thanks for the time, and for the work you all put into the repository.
