package pl.touk.esp.ui.api.helpers

import db.migration.DefaultJdbcProfile
import pl.touk.esp.engine.api.deployment.{ProcessDeploymentData, ProcessManager, ProcessState}
import pl.touk.esp.ui.api.{ProcessPosting, ProcessValidation, ValidationTestData}
import pl.touk.esp.ui.process.marshall.ProcessConverter
import pl.touk.esp.ui.process.repository.{DeployedProcessRepository, ProcessRepository}
import slick.jdbc.JdbcBackend

import scala.concurrent.Future

object TestFactory {

  val processValidation = new ProcessValidation(ValidationTestData.validator)
  val processConverter = new ProcessConverter(processValidation)
  val posting = new ProcessPosting(processConverter)

  def newProcessRepository(db: JdbcBackend.Database) = new ProcessRepository(db, DefaultJdbcProfile.profile, processConverter)
  def newDeploymentProcessRepository(db: JdbcBackend.Database) = new DeployedProcessRepository(db, DefaultJdbcProfile.profile)
  val mockProcessManager = InMemoryMocks.mockProcessManager

  object InMemoryMocks {

    val mockProcessManager = new ProcessManager {
      override def findJobStatus(name: String): Future[Option[ProcessState]] = Future.successful(None)
      override def cancel(name: String): Future[Unit] = Future.successful(Unit)
      override def deploy(processId: String, processDeploymentData: ProcessDeploymentData): Future[Unit] = Future.successful(Unit)
    }
  }
}
