package com.example.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AppDatabase
import com.example.data.local.TicketDao
import com.example.data.model.PaUserEntity
import com.example.data.model.TicketEntity
import com.example.data.remote.FirestoreTicketMapper
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class FirestoreSyncAutoTest {

    private lateinit var database: AppDatabase
    private lateinit var ticketDao: TicketDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        ticketDao = database.ticketDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `test Firestore document mapping from ticket portal`() {
        val firestoreMap = mapOf(
            "ticketCode" to "#001",
            "citizenName" to "Ravi Teja",
            "phoneNumber" to "9848022338",
            "category" to "Personal",
            "subCategory" to "Medical Aid",
            "description" to "Urgent financial aid for heart surgery",
            "location" to "Ward 14, Old Town",
            "assignedPa" to "PA Ramesh",
            "status" to "Unsolved",
            "isSolved" to false,
            "priority" to "High",
            "createdAt" to 1700000000000L
        )

        val ticket = FirestoreTicketMapper.fromMap(firestoreMap, fallbackId = "001")

        assertEquals("#001", ticket.ticketCode)
        assertEquals("Ravi Teja", ticket.citizenName)
        assertEquals("9848022338", ticket.phoneNumber)
        assertEquals("Personal", ticket.category)
        assertEquals("Medical Aid", ticket.subCategory)
        assertFalse("Ticket should be unsolved", ticket.isSolved)
        assertEquals("High", ticket.priority)
    }

    @Test
    fun `test submitterName prioritized over pa name so submitter is shown in leader portal`() {
        // Ticket portal inputs submitterName for the constituent, while name contains PA name
        val portalMap = mapOf(
            "ticketCode" to "#PORTAL-501",
            "submitterName" to "M. Venkateswarlu",
            "name" to "PA Ramesh",
            "phoneNumber" to "9848123456",
            "category" to "Issue",
            "subCategory" to "Street Light Repair",
            "description" to "Pole 14 light blinking",
            "location" to "Gandhi Road",
            "status" to "Unsolved"
        )

        val ticket = FirestoreTicketMapper.fromMap(portalMap, fallbackId = "doc_501")

        // Submitter name must be extracted for the ticket's citizenName (shown in Name overview)
        assertEquals("M. Venkateswarlu", ticket.citizenName)
        assertNotEquals("PA Ramesh", ticket.citizenName)
        assertNotEquals("Citizen", ticket.citizenName)

        // PA name must be extracted for assignedPa
        assertEquals("PA Ramesh", ticket.assignedPa)
        assertEquals("9848123456", ticket.phoneNumber)
    }

    @Test
    fun `test submitter_name and Submitter Name variations`() {
        val underscoreMap = mapOf(
            "submitter_name" to "G. Satyanarayana",
            "pa" to "PA Suresh",
            "mobile" to "9123456780"
        )
        val t1 = FirestoreTicketMapper.fromMap(underscoreMap, fallbackId = "doc_1")
        assertEquals("G. Satyanarayana", t1.citizenName)
        assertEquals("PA Suresh", t1.assignedPa)

        val spacedMap = mapOf(
            "Submitter Name" to "K. Padmavathi",
            "paName" to "PA Priya",
            "contact" to "9876543211"
        )
        val t2 = FirestoreTicketMapper.fromMap(spacedMap, fallbackId = "doc_2")
        assertEquals("K. Padmavathi", t2.citizenName)
        assertEquals("PA Priya", t2.assignedPa)
    }

    @Test
    fun `test exact citizen name and numeric phone extraction across schema variations`() {
        // Portal uses "fullName" and numeric "mobile" (Long)
        val variantMap = mapOf(
            "fullName" to "Jeevan Kumar",
            "mobile" to 8978925540L,
            "title" to "Road Drainage Overflow",
            "details" to "Water stagnating near main junction",
            "area" to "Ward 5",
            "status" to "Unsolved"
        )

        val ticket = FirestoreTicketMapper.fromMap(variantMap, fallbackId = "doc_789")

        assertEquals("Jeevan Kumar", ticket.citizenName)
        assertNotEquals("Citizen", ticket.citizenName)
        assertEquals("8978925540", ticket.phoneNumber)
        assertEquals("#doc_789", ticket.ticketCode)
        assertEquals("Road Drainage Overflow", ticket.subCategory)
        assertFalse(ticket.isSolved)
    }

    @Test
    fun `test nested citizen object extraction`() {
        // Portal uses nested object: citizen: { name: "...", phone: "..." }
        val nestedMap = mapOf(
            "citizen" to mapOf(
                "name" to "Sunita Devi",
                "phone" to "9876543210"
            ),
            "description" to "Ration card address update pending",
            "category" to "Personal",
            "status" to "Solved"
        )

        val ticket = FirestoreTicketMapper.fromMap(nestedMap, fallbackId = "doc_456")

        assertEquals("Sunita Devi", ticket.citizenName)
        assertNotEquals("Citizen", ticket.citizenName)
        assertEquals("9876543210", ticket.phoneNumber)
        assertEquals("Personal", ticket.category)
        assertTrue(ticket.isSolved)
    }

    @Test
    fun `test deleteTicketsByCitizenNames clears initial sample tickets when real tickets arrive`() = runBlocking {
        val sampleNames = listOf("Ravi Teja", "Anuradha Rao", "K. Suresh Reddy", "B. Lakshmi Devi", "Venkatesh Naidu")
        for (name in sampleNames) {
            ticketDao.insertTicket(
                TicketEntity(
                    ticketCode = "#sample_${name.take(3)}",
                    citizenName = name,
                    phoneNumber = "9000000000",
                    category = "Issue",
                    subCategory = "Test",
                    description = "Sample ticket",
                    location = "City",
                    assignedPa = "PA Suresh"
                )
            )
        }

        assertEquals(5, ticketDao.getTicketCount())

        val realTicket = TicketEntity(
            ticketCode = "#PORTAL-101",
            citizenName = "Real Constituent Mahesh",
            phoneNumber = "9848011223",
            category = "Issue",
            subCategory = "Drainage",
            description = "Blocked drain",
            location = "Ward 3",
            assignedPa = "PA Ramesh"
        )

        // Clean up sample tickets
        ticketDao.deleteTicketsByCitizenNames(sampleNames)
        ticketDao.insertTicket(realTicket)

        val remainingTickets = ticketDao.getAllTickets().first()
        assertEquals(1, remainingTickets.size)
        assertEquals("Real Constituent Mahesh", remainingTickets[0].citizenName)
    }

    @Test
    fun `test instant status update from unsolved to solved in database`() = runBlocking {
        val initialTicket = TicketEntity(
            ticketCode = "#002",
            citizenName = "Anuradha Rao",
            phoneNumber = "9121045678",
            category = "Issue",
            subCategory = "Water Pipeline",
            description = "Main drinking water pipe leakage",
            location = "Gandhi Nagar",
            assignedPa = "PA Suresh",
            isSolved = false,
            priority = "High"
        )
        ticketDao.insertTicket(initialTicket)

        val updatedFirestoreMap = mapOf(
            "ticketCode" to "#002",
            "submitterName" to "Anuradha Rao",
            "phoneNumber" to "9121045678",
            "category" to "Issue",
            "subCategory" to "Water Pipeline",
            "description" to "Main drinking water pipe leakage",
            "location" to "Gandhi Nagar",
            "assignedPa" to "PA Suresh",
            "status" to "Solved",
            "isSolved" to true,
            "resolutionNotes" to "Municipality patched valve; water supply restored.",
            "priority" to "High",
            "solvedAt" to 1700005000000L
        )

        val remoteTicket = FirestoreTicketMapper.fromMap(updatedFirestoreMap, "002")
        assertTrue("Mapped remote ticket should be solved", remoteTicket.isSolved)

        val localTicket = ticketDao.getTicketByCode(remoteTicket.ticketCode)
        assertNotNull(localTicket)
        ticketDao.updateTicket(remoteTicket.copy(id = localTicket!!.id))

        val allTickets = ticketDao.getAllTickets().first()
        val updated = allTickets.find { it.ticketCode == "#002" }
        assertNotNull(updated)
        assertTrue("Status must be instantly updated to Solved in Leader Portal", updated!!.isSolved)
        assertEquals("Municipality patched valve; water supply restored.", updated.resolutionNotes)
    }

    @Test
    fun `test serialization to Firestore document map`() {
        val ticket = TicketEntity(
            ticketCode = "#003",
            citizenName = "K. Suresh Reddy",
            phoneNumber = "9849112233",
            category = "Personal",
            subCategory = "Pension",
            description = "Disbursement pending",
            location = "Bhavani Puram",
            assignedPa = "PA Priya",
            isSolved = true,
            resolutionNotes = "Verified and released",
            priority = "Medium"
        )

        val map = FirestoreTicketMapper.toMap(ticket)

        assertEquals("#003", map["ticketCode"])
        assertEquals("K. Suresh Reddy", map["citizenName"])
        assertEquals("K. Suresh Reddy", map["submitterName"])
        assertEquals(true, map["isSolved"])
        assertEquals("Solved", map["status"])
    }

    @Test
    fun `test auto sync deletes data in leader portal when ticket is deleted from firestore`() = runBlocking {
        val syncService = com.example.data.remote.FirestoreTicketSyncService(ticketDao, null)

        // Given: 2 tickets in the local database
        val ticket1 = TicketEntity(
            ticketCode = "#DOC_100",
            citizenName = "Ramesh Babu",
            phoneNumber = "9876500001",
            category = "Issue",
            subCategory = "Drainage",
            description = "Blocked drain",
            location = "Main Bazar",
            assignedPa = "PA Suresh"
        )
        val ticket2 = TicketEntity(
            ticketCode = "#DOC_200",
            citizenName = "Sita Devi",
            phoneNumber = "9876500002",
            category = "Personal",
            subCategory = "Medical Aid",
            description = "Hospital assistance",
            location = "Colony 2",
            assignedPa = "PA Priya"
        )

        ticketDao.insertTicket(ticket1)
        ticketDao.insertTicket(ticket2)
        assertEquals(2, ticketDao.getAllTickets().first().size)

        // When: DOC_100 is deleted from Firestore and syncService processes deletion
        syncService.deleteTicketByIdOrCode("DOC_100")

        // Then: DOC_100 is deleted from local database, ticket2 remains
        val remaining = ticketDao.getAllTickets().first()
        assertEquals(1, remaining.size)
        assertEquals("#DOC_200", remaining[0].ticketCode)
        assertEquals("Sita Devi", remaining[0].citizenName)

        // When: DOC_200 is deleted with hash prefix
        syncService.deleteTicketByIdOrCode("#DOC_200")
        val emptyList = ticketDao.getAllTickets().first()
        assertTrue(emptyList.isEmpty())
    }

    @Test
    fun `test deleteTicketsByCodes correctly removes multiple tickets`() = runBlocking {
        ticketDao.insertTicket(
            TicketEntity(
                ticketCode = "#T1",
                citizenName = "User 1",
                phoneNumber = "9000000001",
                category = "Issue",
                subCategory = "Road",
                description = "Pothole",
                location = "Street 1",
                assignedPa = "PA 1"
            )
        )
        ticketDao.insertTicket(
            TicketEntity(
                ticketCode = "#T2",
                citizenName = "User 2",
                phoneNumber = "9000000002",
                category = "Issue",
                subCategory = "Light",
                description = "Dark lamp",
                location = "Street 2",
                assignedPa = "PA 2"
            )
        )

        val deletedCount = ticketDao.deleteTicketsByCodes(listOf("#T1", "#T2"))
        assertEquals(2, deletedCount)
        assertTrue(ticketDao.getAllTickets().first().isEmpty())
    }

    @Test
    fun `test PA credentials creation, retrieval, and deletion in Room DAO`() = runBlocking {
        val pa1 = PaUserEntity(
            paName = "PA Ramesh",
            phoneNumber = "9876543210",
            password = "ramesh_password123",
            designation = "Senior PA",
            status = "Active"
        )
        val pa2 = PaUserEntity(
            paName = "PA Suresh",
            phoneNumber = "9876543211",
            password = "suresh_password123",
            designation = "Personal Assistant",
            status = "Active"
        )

        ticketDao.insertPaUser(pa1)
        ticketDao.insertPaUser(pa2)

        val allPas = ticketDao.getAllPaUsers().first()
        assertEquals(2, allPas.size)

        val fetchedPa = ticketDao.getPaByPhone("9876543210")
        assertNotNull(fetchedPa)
        assertEquals("PA Ramesh", fetchedPa?.paName)
        assertEquals("ramesh_password123", fetchedPa?.password)

        // Delete PA Ramesh
        ticketDao.deletePaUserByPhone("9876543210")
        val remainingPas = ticketDao.getAllPaUsers().first()
        assertEquals(1, remainingPas.size)
        assertEquals("PA Suresh", remainingPas[0].paName)
    }

    @Test
    fun `test addedBy PA name extracted from Firestore schema variants`() {
        // Variant 1: addedBy string
        val mapWithAddedBy = mapOf(
            "ticketCode" to "#TEST-1",
            "citizenName" to "Ramu",
            "phoneNumber" to "9988776655",
            "addedBy" to "PA Ramesh",
            "category" to "Personal"
        )
        val t1 = FirestoreTicketMapper.fromMap(mapWithAddedBy, "doc_1")
        assertEquals("PA Ramesh", t1.assignedPa)
        assertEquals("PA Ramesh", t1.addedByPaName)

        // Variant 2: nested addedBy object
        val mapWithNestedAddedBy = mapOf(
            "ticketCode" to "#TEST-2",
            "citizenName" to "Sita",
            "phoneNumber" to "9988776644",
            "addedBy" to mapOf("name" to "PA Priya", "role" to "PA"),
            "category" to "Issue"
        )
        val t2 = FirestoreTicketMapper.fromMap(mapWithNestedAddedBy, "doc_2")
        assertEquals("PA Priya", t2.assignedPa)
        assertEquals("PA Priya", t2.addedByPaName)

        // Variant 3: createdBy and citizen name in 'name'
        val mapWithCreatedBy = mapOf(
            "ticketCode" to "#TEST-3",
            "name" to "Gopal",
            "phoneNumber" to "9988776633",
            "createdBy" to "PA Suresh",
            "category" to "Issue"
        )
        val t3 = FirestoreTicketMapper.fromMap(mapWithCreatedBy, "doc_3")
        assertEquals("Gopal", t3.citizenName)
        assertEquals("PA Suresh", t3.assignedPa)
        assertEquals("PA Suresh", t3.addedByPaName)

        // Variant 4: reports list with addedBy
        val mapWithReports = mapOf(
            "ticketCode" to "#TEST-4",
            "citizenName" to "Lakshmi",
            "phoneNumber" to "9988776622",
            "reports" to listOf(
                mapOf(
                    "addedBy" to "PA Harish",
                    "category" to "Issue",
                    "subject" to "Road",
                    "description" to "Pothole",
                    "timestamp" to 1700000000000L
                )
            )
        )
        val t4 = FirestoreTicketMapper.fromMap(mapWithReports, "doc_4")
        assertEquals("PA Harish", t4.assignedPa)
        assertEquals("PA Harish", t4.addedByPaName)

        // Variant 5: phone number in addedBy resolved via paLookup
        val mapWithPhoneAddedBy = mapOf(
            "ticketCode" to "#TEST-5",
            "citizenName" to "Naveen",
            "phoneNumber" to "9988776611",
            "addedBy" to "9876543210",
            "category" to "Personal"
        )
        val t5 = FirestoreTicketMapper.fromMap(
            mapWithPhoneAddedBy,
            "doc_5",
            paLookup = { phone -> if (phone == "9876543210") "PA Venkatesh" else null }
        )
        assertEquals("PA Venkatesh", t5.assignedPa)
        assertEquals("PA Venkatesh", t5.addedByPaName)

        // Variant 6: Ignore placeholder "PA" in 'pa' field when real name is in 'author' or 'addedByName'
        val mapWithPlaceholder = mapOf(
            "ticketCode" to "#TEST-6",
            "citizenName" to "Chandrasekhar",
            "phoneNumber" to "9988776600",
            "pa" to "PA",
            "author" to "PA Deepthi",
            "category" to "Issue"
        )
        val t6 = FirestoreTicketMapper.fromMap(mapWithPlaceholder, "doc_6")
        assertEquals("PA Deepthi", t6.assignedPa)
        assertEquals("PA Deepthi", t6.addedByPaName)
    }
}

