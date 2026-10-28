package app.miogram

import androidx.media3.database.StandaloneDatabaseProvider
import androidx.test.core.app.ApplicationProvider
import app.miogram.connection.ConnectionKeeper
import app.miogram.data.telegram.AppConfigRepository
import app.miogram.data.telegram.ArticlesRepository
import app.miogram.data.telegram.AttachRepository
import app.miogram.data.telegram.AuthRepository
import app.miogram.data.telegram.BotsRepository
import app.miogram.data.telegram.ChannelsRepository
import app.miogram.data.telegram.ContactsRepository
import app.miogram.data.telegram.ScheduledRepository
import app.miogram.data.telegram.DialogsRepository
import app.miogram.data.telegram.EphemeralRepository
import app.miogram.data.telegram.ForumRepository
import app.miogram.data.telegram.GiftsRepository
import app.miogram.data.telegram.GroupsRepository
import app.miogram.data.telegram.InvitesRepository
import app.miogram.data.telegram.LinksRepository
import app.miogram.data.telegram.MediaRepository
import app.miogram.data.telegram.MessagesRepository
import app.miogram.data.telegram.NotificationMediaRepository
import app.miogram.data.telegram.NotifySettingsRepository
import app.miogram.data.telegram.ProfileEditRepository
import app.miogram.data.telegram.ProfileRepository
import app.miogram.data.telegram.PrivacyRepository
import app.miogram.data.telegram.PollsRepository
import app.miogram.data.telegram.PinsRepository
import app.miogram.data.telegram.TranslationsRepository
import app.miogram.data.telegram.PushRepository
import app.miogram.data.telegram.ReactionsRepository
import app.miogram.data.telegram.SafetyRepository
import app.miogram.data.telegram.StickersRepository
import app.miogram.data.telegram.StoriesRepository
import app.miogram.data.telegram.TelegramClientHolder
import app.miogram.data.telegram.TwoStepRepository
import app.miogram.data.telegram.UpdatesRepository
import app.miogram.data.telegram.WallpapersRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.flowOf
import java.io.File

class TestRepositories(dir: File, fake: FakeTelegram, val telegram: TelegramClientHolder = TelegramClientHolder(dir) { id, hash, logger -> fake.config(id, hash, logger) }) {
    val media = MediaRepository(telegram) { StandaloneDatabaseProvider(ApplicationProvider.getApplicationContext()) }
    val auth = AuthRepository(telegram, media)
    val appConfig = AppConfigRepository(telegram)
    val dialogs = DialogsRepository(telegram, appConfig)
    val messages = MessagesRepository(telegram)
    val profiles = ProfileRepository(telegram)
    val updates = UpdatesRepository(telegram)
    val push = PushRepository(telegram, auth)
    val notificationMedia = NotificationMediaRepository(telegram)
    val privacy = PrivacyRepository(telegram)
    val twoStep = TwoStepRepository(telegram)
    val stickers = StickersRepository(telegram)
    val gifts = GiftsRepository(telegram)
    val links = LinksRepository(telegram)
    val channels = ChannelsRepository(telegram)
    val invites = InvitesRepository(telegram)
    val groups = GroupsRepository(telegram, invites)
    val forum = ForumRepository(telegram)
    val bots = BotsRepository(telegram)
    val reactions = ReactionsRepository(telegram, appConfig)
    val ephemeral = EphemeralRepository(telegram)
    val articles = ArticlesRepository(telegram, appConfig)
    val polls = PollsRepository(telegram)
    val attach = AttachRepository(telegram)
    val pins = PinsRepository(telegram)
    val translations = TranslationsRepository(telegram)
    val wallpapers = WallpapersRepository(telegram)
    val contacts = ContactsRepository(telegram)
    val safety = SafetyRepository(telegram)
    val profileEdits = ProfileEditRepository(telegram, profiles, appConfig)
    val scheduled = ScheduledRepository(telegram)
    val shared = app.miogram.data.telegram.SharedMediaRepository(telegram)

    val stories by lazy { StoriesRepository(telegram, updates, CoroutineScope(SupervisorJob() + Dispatchers.Default)) }

    val notifySettings = NotifySettingsRepository(telegram, updates)
}

fun keptConnected(telegram: TelegramClientHolder, scope: CoroutineScope) = ConnectionKeeper(telegram, flowOf(false), scope, ConnectionKeeper.IDLE_MILLIS) { System.nanoTime() / 1_000_000 }
