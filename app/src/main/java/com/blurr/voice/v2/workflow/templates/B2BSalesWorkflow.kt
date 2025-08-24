package com.blurr.voice.v2.workflow.templates

import com.blurr.voice.v2.workflow.models.*

object B2BSalesWorkflow {
    
    fun createSalesManagerWorkflow(): Workflow {
        return Workflow(
            id = "b2b_sales_manager",
            name = "B2B Sales Manager Automation",
            description = "Automated B2B lead generation, outreach, and meeting scheduling",
            steps = listOf(
                // Phase 1: Lead Research
                WorkflowStep(
                    id = "1",
                    name = "Open Google Sheets",
                    action = ActionType.LAUNCH_APP,
                    targetApp = "com.google.android.apps.docs.editors.sheets",
                    timeout = 10000L
                ),
                WorkflowStep(
                    id = "2",
                    name = "Read prospect list",
                    action = ActionType.EXTRACT_DATA,
                    parameters = mapOf(
                        "columns" to listOf("name", "company", "role", "status"),
                        "max_rows" to 50
                    ),
                    dependencies = listOf("1")
                ),
                WorkflowStep(
                    id = "3",
                    name = "Open LinkedIn",
                    action = ActionType.LAUNCH_APP,
                    targetApp = "com.linkedin.android",
                    dependencies = listOf("2")
                ),
                WorkflowStep(
                    id = "4",
                    name = "Research prospects",
                    action = ActionType.LOOP,
                    parameters = mapOf(
                        "items" to "{prospects_list}",
                        "actions" to listOf(
                            "search_person",
                            "view_profile",
                            "extract_info",
                            "check_recent_activity"
                        )
                    ),
                    dependencies = listOf("3"),
                    timeout = 60000L
                ),
                
                // Phase 2: Personalized Outreach
                WorkflowStep(
                    id = "5",
                    name = "Generate personalized messages",
                    action = ActionType.COMPLEX_SEQUENCE,
                    parameters = mapOf(
                        "template" to """
                        Hi {name},
                        
                        I noticed you're {role} at {company} and saw your recent post about {recent_activity}.
                        
                        {value_proposition}
                        
                        Would you be open to a brief 15-minute call to discuss how we could help {company} with {pain_point}?
                        """,
                        "personalization_fields" to listOf("recent_activity", "pain_point")
                    ),
                    dependencies = listOf("4")
                ),
                WorkflowStep(
                    id = "6",
                    name = "Send LinkedIn messages",
                    action = ActionType.SEND_MESSAGE,
                    parameters = mapOf(
                        "platform" to "linkedin",
                        "message_type" to "connection_request",
                        "rate_limit" to 20, // per day
                        "delay_between" to 30000 // 30 seconds
                    ),
                    dependencies = listOf("5"),
                    requiresConfirmation = true
                ),
                WorkflowStep(
                    id = "7",
                    name = "Open email app",
                    action = ActionType.LAUNCH_APP,
                    targetApp = "com.google.android.gm",
                    dependencies = listOf("6")
                ),
                WorkflowStep(
                    id = "8",
                    name = "Send follow-up emails",
                    action = ActionType.SEND_MESSAGE,
                    parameters = mapOf(
                        "platform" to "email",
                        "subject" to "Quick question about {company}'s {department}",
                        "track_opens" to true
                    ),
                    dependencies = listOf("7")
                ),
                
                // Phase 3: WhatsApp Outreach
                WorkflowStep(
                    id = "9",
                    name = "Open WhatsApp Business",
                    action = ActionType.LAUNCH_APP,
                    targetApp = "com.whatsapp.w4b",
                    dependencies = listOf("8")
                ),
                WorkflowStep(
                    id = "10",
                    name = "Send WhatsApp messages",
                    action = ActionType.SEND_MESSAGE,
                    parameters = mapOf(
                        "platform" to "whatsapp",
                        "message_type" to "initial_outreach",
                        "include_company_intro" to true
                    ),
                    dependencies = listOf("9"),
                    requiresConfirmation = true
                ),
                
                // Phase 4: Response Monitoring
                WorkflowStep(
                    id = "11",
                    name = "Monitor responses",
                    action = ActionType.WAIT,
                    parameters = mapOf(
                        "duration" to 86400000L, // 24 hours
                        "check_interval" to 3600000L, // hourly
                        "platforms" to listOf("linkedin", "email", "whatsapp")
                    ),
                    dependencies = listOf("10")
                ),
                WorkflowStep(
                    id = "12",
                    name = "Categorize responses",
                    action = ActionType.COMPLEX_SEQUENCE,
                    parameters = mapOf(
                        "categories" to listOf(
                            "interested_immediate",
                            "interested_future",
                            "needs_more_info",
                            "not_interested"
                        )
                    ),
                    dependencies = listOf("11")
                ),
                
                // Phase 5: Meeting Scheduling
                WorkflowStep(
                    id = "13",
                    name = "Open Google Calendar",
                    action = ActionType.LAUNCH_APP,
                    targetApp = "com.google.android.calendar",
                    dependencies = listOf("12")
                ),
                WorkflowStep(
                    id = "14",
                    name = "Check availability",
                    action = ActionType.EXTRACT_DATA,
                    parameters = mapOf(
                        "date_range" to "next_2_weeks",
                        "slot_duration" to 30,
                        "preferred_times" to listOf("10:00", "14:00", "16:00")
                    ),
                    dependencies = listOf("13")
                ),
                WorkflowStep(
                    id = "15",
                    name = "Send calendar invites",
                    action = ActionType.CREATE_EVENT,
                    parameters = mapOf(
                        "event_type" to "sales_call",
                        "duration" to 30,
                        "include_zoom_link" to true,
                        "reminder" to 15
                    ),
                    dependencies = listOf("14")
                ),
                
                // Phase 6: Update CRM/Sheets
                WorkflowStep(
                    id = "16",
                    name = "Return to Google Sheets",
                    action = ActionType.LAUNCH_APP,
                    targetApp = "com.google.android.apps.docs.editors.sheets",
                    dependencies = listOf("15")
                ),
                WorkflowStep(
                    id = "17",
                    name = "Update prospect status",
                    action = ActionType.TYPE,
                    parameters = mapOf(
                        "updates" to mapOf(
                            "status" to "{response_category}",
                            "last_contact" to "{current_date}",
                            "meeting_scheduled" to "{meeting_date}",
                            "notes" to "{interaction_summary}"
                        )
                    ),
                    dependencies = listOf("16")
                )
            )
        )
    }
    
    fun createInstagramFollowWorkflow(): Workflow {
        return Workflow(
            id = "instagram_bulk_follow",
            name = "Instagram Bulk Follow from Sheet",
            description = "Follow Instagram accounts from Google Sheets list",
            steps = listOf(
                WorkflowStep(
                    id = "1",
                    name = "Open Google Sheets",
                    action = ActionType.LAUNCH_APP,
                    targetApp = "com.google.android.apps.docs.editors.sheets"
                ),
                WorkflowStep(
                    id = "2",
                    name = "Extract Instagram handles",
                    action = ActionType.EXTRACT_DATA,
                    parameters = mapOf(
                        "column" to "instagram_handle",
                        "max_rows" to 50
                    ),
                    dependencies = listOf("1")
                ),
                WorkflowStep(
                    id = "3",
                    name = "Open Instagram",
                    action = ActionType.LAUNCH_APP,
                    targetApp = "com.instagram.android",
                    dependencies = listOf("2")
                ),
                WorkflowStep(
                    id = "4",
                    name = "Follow accounts with anti-bot delays",
                    action = ActionType.LOOP,
                    parameters = mapOf(
                        "items" to "{instagram_handles}",
                        "actions" to listOf(
                            mapOf(
                                "action" to "search_user",
                                "delay_after" to 3000
                            ),
                            mapOf(
                                "action" to "open_profile",
                                "delay_after" to 2000
                            ),
                            mapOf(
                                "action" to "tap_follow",
                                "delay_after" to 5000
                            ),
                            mapOf(
                                "action" to "random_scroll",
                                "delay_after" to 2000
                            )
                        ),
                        "batch_size" to 10,
                        "batch_delay" to 300000, // 5 min between batches
                        "daily_limit" to 50,
                        "randomize_delays" to true
                    ),
                    dependencies = listOf("3"),
                    timeout = 3600000L // 1 hour max
                ),
                WorkflowStep(
                    id = "5",
                    name = "Update follow status in Sheets",
                    action = ActionType.LAUNCH_APP,
                    targetApp = "com.google.android.apps.docs.editors.sheets",
                    dependencies = listOf("4")
                ),
                WorkflowStep(
                    id = "6",
                    name = "Mark as followed",
                    action = ActionType.TYPE,
                    parameters = mapOf(
                        "column" to "follow_status",
                        "value" to "followed",
                        "date_column" to "follow_date"
                    ),
                    dependencies = listOf("5")
                )
            )
        )
    }
}