package com.blurr.voice.v2.workflow.templates

import com.blurr.voice.v2.workflow.models.*

object WorkflowTemplates {
    
    fun getTemplate(templateName: String): Workflow? {
        return when (templateName.lowercase()) {
            "hire_freelancer" -> hireFreelancerTemplate()
            "schedule_meeting" -> scheduleMeetingTemplate()
            "research_and_report" -> researchAndReportTemplate()
            "social_media_post" -> socialMediaPostTemplate()
            "online_shopping" -> onlineShoppingTemplate()
            "travel_booking" -> travelBookingTemplate()
            "job_application" -> jobApplicationTemplate()
            "food_delivery" -> foodDeliveryTemplate()
            else -> null
        }
    }
    
    private fun hireFreelancerTemplate(): Workflow {
        return Workflow(
            id = "hire_freelancer_template",
            name = "Hire Freelancer",
            description = "Find and hire freelancers on platforms like Upwork",
            steps = listOf(
                WorkflowStep(
                    id = "1",
                    name = "Open freelance platform",
                    action = ActionType.LAUNCH_APP,
                    targetApp = "com.upwork.android.apps.main",
                    timeout = 15000L
                ),
                WorkflowStep(
                    id = "2",
                    name = "Navigate to search",
                    action = ActionType.TAP,
                    parameters = mapOf("target" to "search_button"),
                    dependencies = listOf("1")
                ),
                WorkflowStep(
                    id = "3",
                    name = "Enter search criteria",
                    action = ActionType.TYPE,
                    parameters = mapOf("text" to "{job_title}"),
                    dependencies = listOf("2")
                ),
                WorkflowStep(
                    id = "4",
                    name = "Apply budget filter",
                    action = ActionType.FILTER,
                    parameters = mapOf(
                        "budget_min" to "{budget_min}",
                        "budget_max" to "{budget_max}"
                    ),
                    dependencies = listOf("3")
                ),
                WorkflowStep(
                    id = "5",
                    name = "Apply experience filter",
                    action = ActionType.FILTER,
                    parameters = mapOf(
                        "experience_level" to "{experience_level}",
                        "skills" to "{required_skills}"
                    ),
                    dependencies = listOf("4")
                ),
                WorkflowStep(
                    id = "6",
                    name = "Analyze profiles",
                    action = ActionType.EXTRACT_DATA,
                    parameters = mapOf(
                        "fields" to listOf(
                            "name",
                            "rating",
                            "hourly_rate",
                            "success_rate",
                            "portfolio_quality",
                            "relevant_experience"
                        ),
                        "max_profiles" to 10
                    ),
                    dependencies = listOf("5"),
                    parallel = true
                ),
                WorkflowStep(
                    id = "7",
                    name = "Score and rank candidates",
                    action = ActionType.COMPLEX_SEQUENCE,
                    parameters = mapOf(
                        "scoring_criteria" to mapOf(
                            "rating" to 0.2,
                            "success_rate" to 0.3,
                            "relevant_experience" to 0.3,
                            "budget_fit" to 0.2
                        )
                    ),
                    dependencies = listOf("6")
                ),
                WorkflowStep(
                    id = "8",
                    name = "Message top candidates",
                    action = ActionType.SEND_MESSAGE,
                    template = "freelancer_outreach",
                    parameters = mapOf(
                        "max_messages" to 5,
                        "personalized" to true
                    ),
                    dependencies = listOf("7"),
                    requiresConfirmation = true
                ),
                WorkflowStep(
                    id = "9",
                    name = "Track responses",
                    action = ActionType.WAIT,
                    parameters = mapOf(
                        "duration" to 86400000L, // 24 hours
                        "check_interval" to 3600000L // 1 hour
                    ),
                    dependencies = listOf("8")
                ),
                WorkflowStep(
                    id = "10",
                    name = "Schedule interviews",
                    action = ActionType.LAUNCH_APP,
                    targetApp = "com.google.android.calendar",
                    dependencies = listOf("9")
                ),
                WorkflowStep(
                    id = "11",
                    name = "Create interview slots",
                    action = ActionType.CREATE_EVENT,
                    parameters = mapOf(
                        "event_type" to "interview",
                        "duration" to "30min",
                        "slots" to "{interview_slots}"
                    ),
                    dependencies = listOf("10")
                )
            )
        )
    }
    
    private fun scheduleMeetingTemplate(): Workflow {
        return Workflow(
            id = "schedule_meeting_template",
            name = "Schedule Meeting",
            description = "Schedule meetings across calendar and communication apps",
            steps = listOf(
                WorkflowStep(
                    id = "1",
                    name = "Check calendar availability",
                    action = ActionType.LAUNCH_APP,
                    targetApp = "com.google.android.calendar"
                ),
                WorkflowStep(
                    id = "2",
                    name = "Find available slots",
                    action = ActionType.EXTRACT_DATA,
                    parameters = mapOf(
                        "date_range" to "{date_range}",
                        "duration" to "{meeting_duration}",
                        "preferred_times" to "{preferred_times}"
                    ),
                    dependencies = listOf("1")
                ),
                WorkflowStep(
                    id = "3",
                    name = "Open communication app",
                    action = ActionType.LAUNCH_APP,
                    targetApp = "{communication_app}",
                    dependencies = listOf("2")
                ),
                WorkflowStep(
                    id = "4",
                    name = "Send meeting invitation",
                    action = ActionType.SEND_MESSAGE,
                    template = "meeting_invitation",
                    parameters = mapOf(
                        "recipients" to "{recipients}",
                        "available_slots" to "{available_slots}",
                        "meeting_link" to "{meeting_link}"
                    ),
                    dependencies = listOf("3")
                ),
                WorkflowStep(
                    id = "5",
                    name = "Wait for confirmation",
                    action = ActionType.WAIT,
                    parameters = mapOf("duration" to 7200000L), // 2 hours
                    dependencies = listOf("4")
                ),
                WorkflowStep(
                    id = "6",
                    name = "Create calendar event",
                    action = ActionType.CREATE_EVENT,
                    parameters = mapOf(
                        "title" to "{meeting_title}",
                        "attendees" to "{confirmed_attendees}",
                        "location" to "{meeting_location}"
                    ),
                    dependencies = listOf("5")
                )
            )
        )
    }
    
    private fun researchAndReportTemplate(): Workflow {
        return Workflow(
            id = "research_report_template",
            name = "Research and Report",
            description = "Research topics across multiple sources and compile reports",
            steps = listOf(
                WorkflowStep(
                    id = "1",
                    name = "Search web sources",
                    action = ActionType.LAUNCH_APP,
                    targetApp = "com.android.chrome"
                ),
                WorkflowStep(
                    id = "2",
                    name = "Perform searches",
                    action = ActionType.SEARCH,
                    parameters = mapOf(
                        "queries" to "{search_queries}",
                        "sources" to "{trusted_sources}"
                    ),
                    dependencies = listOf("1"),
                    parallel = true
                ),
                WorkflowStep(
                    id = "3",
                    name = "Extract information",
                    action = ActionType.EXTRACT_DATA,
                    parameters = mapOf(
                        "extract_type" to "article_content",
                        "summary_length" to "medium"
                    ),
                    dependencies = listOf("2")
                ),
                WorkflowStep(
                    id = "4",
                    name = "Open document app",
                    action = ActionType.LAUNCH_APP,
                    targetApp = "com.google.android.apps.docs",
                    dependencies = listOf("3")
                ),
                WorkflowStep(
                    id = "5",
                    name = "Create report",
                    action = ActionType.TYPE,
                    parameters = mapOf(
                        "template" to "research_report",
                        "sections" to "{report_sections}"
                    ),
                    dependencies = listOf("4")
                ),
                WorkflowStep(
                    id = "6",
                    name = "Share report",
                    action = ActionType.SEND_MESSAGE,
                    parameters = mapOf(
                        "recipients" to "{report_recipients}",
                        "share_link" to true
                    ),
                    dependencies = listOf("5")
                )
            )
        )
    }
    
    private fun socialMediaPostTemplate(): Workflow {
        return Workflow(
            id = "social_media_post_template",
            name = "Social Media Post",
            description = "Create and post content across multiple social media platforms",
            steps = listOf(
                WorkflowStep(
                    id = "1",
                    name = "Create content",
                    action = ActionType.COMPLEX_SEQUENCE,
                    parameters = mapOf(
                        "content_type" to "{content_type}",
                        "message" to "{message}",
                        "hashtags" to "{hashtags}"
                    )
                ),
                WorkflowStep(
                    id = "2",
                    name = "Post to Instagram",
                    action = ActionType.LAUNCH_APP,
                    targetApp = "com.instagram.android",
                    dependencies = listOf("1")
                ),
                WorkflowStep(
                    id = "3",
                    name = "Upload to Instagram",
                    action = ActionType.COMPLEX_SEQUENCE,
                    dependencies = listOf("2")
                ),
                WorkflowStep(
                    id = "4",
                    name = "Post to Twitter",
                    action = ActionType.LAUNCH_APP,
                    targetApp = "com.twitter.android",
                    dependencies = listOf("1")
                ),
                WorkflowStep(
                    id = "5",
                    name = "Upload to Twitter",
                    action = ActionType.COMPLEX_SEQUENCE,
                    dependencies = listOf("4")
                ),
                WorkflowStep(
                    id = "6",
                    name = "Post to LinkedIn",
                    action = ActionType.LAUNCH_APP,
                    targetApp = "com.linkedin.android",
                    dependencies = listOf("1")
                ),
                WorkflowStep(
                    id = "7",
                    name = "Upload to LinkedIn",
                    action = ActionType.COMPLEX_SEQUENCE,
                    dependencies = listOf("6")
                )
            )
        )
    }
    
    private fun onlineShoppingTemplate(): Workflow {
        return Workflow(
            id = "online_shopping_template",
            name = "Online Shopping",
            description = "Search, compare, and purchase products online",
            steps = listOf(
                WorkflowStep(
                    id = "1",
                    name = "Open shopping app",
                    action = ActionType.LAUNCH_APP,
                    targetApp = "{shopping_app}"
                ),
                WorkflowStep(
                    id = "2",
                    name = "Search product",
                    action = ActionType.SEARCH,
                    parameters = mapOf("query" to "{product_name}"),
                    dependencies = listOf("1")
                ),
                WorkflowStep(
                    id = "3",
                    name = "Apply filters",
                    action = ActionType.FILTER,
                    parameters = mapOf(
                        "price_range" to "{price_range}",
                        "brand" to "{preferred_brands}",
                        "rating" to "{min_rating}"
                    ),
                    dependencies = listOf("2")
                ),
                WorkflowStep(
                    id = "4",
                    name = "Compare products",
                    action = ActionType.EXTRACT_DATA,
                    parameters = mapOf(
                        "compare_fields" to listOf("price", "rating", "reviews", "shipping")
                    ),
                    dependencies = listOf("3")
                ),
                WorkflowStep(
                    id = "5",
                    name = "Add to cart",
                    action = ActionType.TAP,
                    parameters = mapOf("target" to "add_to_cart"),
                    dependencies = listOf("4"),
                    requiresConfirmation = true
                ),
                WorkflowStep(
                    id = "6",
                    name = "Proceed to checkout",
                    action = ActionType.TAP,
                    parameters = mapOf("target" to "checkout"),
                    dependencies = listOf("5")
                )
            )
        )
    }
    
    private fun travelBookingTemplate(): Workflow {
        return Workflow(
            id = "travel_booking_template",
            name = "Travel Booking",
            description = "Book flights, hotels, and transportation",
            steps = listOf(
                WorkflowStep(
                    id = "1",
                    name = "Search flights",
                    action = ActionType.LAUNCH_APP,
                    targetApp = "{travel_app}"
                ),
                WorkflowStep(
                    id = "2",
                    name = "Enter travel details",
                    action = ActionType.COMPLEX_SEQUENCE,
                    parameters = mapOf(
                        "origin" to "{origin}",
                        "destination" to "{destination}",
                        "dates" to "{travel_dates}",
                        "passengers" to "{passenger_count}"
                    ),
                    dependencies = listOf("1")
                ),
                WorkflowStep(
                    id = "3",
                    name = "Compare flights",
                    action = ActionType.EXTRACT_DATA,
                    dependencies = listOf("2")
                ),
                WorkflowStep(
                    id = "4",
                    name = "Search hotels",
                    action = ActionType.COMPLEX_SEQUENCE,
                    parameters = mapOf(
                        "location" to "{destination}",
                        "check_in" to "{check_in_date}",
                        "check_out" to "{check_out_date}"
                    ),
                    dependencies = listOf("3")
                ),
                WorkflowStep(
                    id = "5",
                    name = "Book selections",
                    action = ActionType.COMPLEX_SEQUENCE,
                    dependencies = listOf("4"),
                    requiresConfirmation = true
                )
            )
        )
    }
    
    private fun jobApplicationTemplate(): Workflow {
        return Workflow(
            id = "job_application_template",
            name = "Job Application",
            description = "Search and apply for jobs across platforms",
            steps = listOf(
                WorkflowStep(
                    id = "1",
                    name = "Open job platform",
                    action = ActionType.LAUNCH_APP,
                    targetApp = "{job_app}"
                ),
                WorkflowStep(
                    id = "2",
                    name = "Search positions",
                    action = ActionType.SEARCH,
                    parameters = mapOf(
                        "job_title" to "{job_title}",
                        "location" to "{location}",
                        "job_type" to "{job_type}"
                    ),
                    dependencies = listOf("1")
                ),
                WorkflowStep(
                    id = "3",
                    name = "Filter results",
                    action = ActionType.FILTER,
                    parameters = mapOf(
                        "salary_range" to "{salary_range}",
                        "experience_level" to "{experience_level}",
                        "company_size" to "{company_size}"
                    ),
                    dependencies = listOf("2")
                ),
                WorkflowStep(
                    id = "4",
                    name = "Analyze job matches",
                    action = ActionType.EXTRACT_DATA,
                    parameters = mapOf(
                        "match_criteria" to "{skills_and_experience}"
                    ),
                    dependencies = listOf("3")
                ),
                WorkflowStep(
                    id = "5",
                    name = "Apply to positions",
                    action = ActionType.COMPLEX_SEQUENCE,
                    parameters = mapOf(
                        "cover_letter_template" to "{cover_letter}",
                        "resume" to "{resume_path}"
                    ),
                    dependencies = listOf("4"),
                    requiresConfirmation = true
                )
            )
        )
    }
    
    private fun foodDeliveryTemplate(): Workflow {
        return Workflow(
            id = "food_delivery_template",
            name = "Food Delivery",
            description = "Order food from delivery apps",
            steps = listOf(
                WorkflowStep(
                    id = "1",
                    name = "Open delivery app",
                    action = ActionType.LAUNCH_APP,
                    targetApp = "{delivery_app}"
                ),
                WorkflowStep(
                    id = "2",
                    name = "Search restaurants",
                    action = ActionType.SEARCH,
                    parameters = mapOf(
                        "cuisine" to "{cuisine_type}",
                        "dietary" to "{dietary_restrictions}"
                    ),
                    dependencies = listOf("1")
                ),
                WorkflowStep(
                    id = "3",
                    name = "Browse menu",
                    action = ActionType.COMPLEX_SEQUENCE,
                    dependencies = listOf("2")
                ),
                WorkflowStep(
                    id = "4",
                    name = "Add items to cart",
                    action = ActionType.COMPLEX_SEQUENCE,
                    parameters = mapOf(
                        "items" to "{food_items}",
                        "customizations" to "{customizations}"
                    ),
                    dependencies = listOf("3")
                ),
                WorkflowStep(
                    id = "5",
                    name = "Checkout",
                    action = ActionType.COMPLEX_SEQUENCE,
                    parameters = mapOf(
                        "delivery_address" to "{address}",
                        "payment_method" to "{payment}"
                    ),
                    dependencies = listOf("4"),
                    requiresConfirmation = true
                )
            )
        )
    }
}