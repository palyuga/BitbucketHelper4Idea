import bitbucket.BitbucketClient
import bitbucket.createClient
import bitbucket.data.PagedResponse
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.StartupActivity
import com.intellij.util.concurrency.AppExecutorUtil
import rx.Observable
import ui.Model
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.function.Consumer

object PostStartupActivity : StartupActivity {
    var future:ScheduledFuture<*>? = null // todo get rid of null, find more right way to store
    var password:CharArray? = null

    override fun runActivity(project: Project) { // todo we have project here, we must use it
        reschedule()
    }

    fun reschedule() {
        if (future != null)
            (future as ScheduledFuture<*>).cancel(true)
        future = AppExecutorUtil.getAppScheduledExecutorService().scheduleWithFixedDelay(
                UpdateTask(createClient()), 0, 15, TimeUnit.SECONDS) as ScheduledFuture<UpdateTask>
    }

    class UpdateTask(private val client: BitbucketClient): Runnable {
        override fun run() {
            process(client.requestReviewedPRs(), Consumer {
                Model.updateReviewingPRs(it)
            })
            process(client.requestOwnPRs(), Consumer {
                Model.updateOwnPRs(it)
            })
        }

        private fun <T> process(obs: Observable<PagedResponse<T>>, consumer: Consumer<List<T>>) {
            try {
            val prs = obs.doOnError { print(it) }
                    .flatMap { Observable.from(it.values) }
                    .toList().toBlocking().toFuture().get()
                consumer.accept(prs)
            } catch (e: Exception) {
                //todo: handle properly
                println(e)
            }
        }
    }
}