<!DOCTYPE html>
<html lang="en">

<head>
    <#include "includes/header.ftlh">
    <link href="vendor/datatables/dataTables.bootstrap4.min.css" rel="stylesheet">
</head>

<body id="page-top">

<!-- Page Wrapper -->
<div id="wrapper">

    <#include "includes/sidebar.ftlh">

    <!-- Content Wrapper -->
    <div id="content-wrapper" class="d-flex flex-column">
        <#include "includes/topbar.ftlh"/>
        <!-- Main Content -->
        <div id="content">


            <!-- Begin Page Content -->
            <div class="container-fluid">
                <div class="row">
                    <div class="col-lg-6 mb-4">
                        <div class="card bg-primary text-white shadow">
                            <select name="msg_collection">
                                <#if msgCollections??>
                                    <#list msgCollections as c>
                                        <option value="${c}" <#if c==coll>selected</#if>>${c}</option>
                                    </#list>
                                <#else>
                                    <option value="none">no message collection found</option>
                                </#if>

                            </select>
                        </div>
                    </div>
                </div>
                <div class="row">
                    <div class="col-lg-6 mb-4">
                        <div class="card bg-primary text-white shadow">
                            <div class="card-body">
                                Unprocessed Messages
                                <div class="text-white-50 small">${pending}</div>
                            </div>
                        </div>
                    </div>
                    <div class="col-lg-6 mb-4">
                        <div class="card bg-success text-white shadow">
                            <div class="card-body">
                                total Messages
                                <div class="text-white-50 small">${total}</div>
                            </div>
                        </div>
                    </div>
                </div>
                <div class="row-cols-1">
                    <div class="card-header">Known clients:</div>
                </div>
                <div class="row">
                    <#list clients as cl>

                        <div class="col-lg-6 mb-4">
                            <div class="card shadow">
                                <div class="card-body">${cl} <a href="">Send direct message</a>
                                </div>
                            </div>
                        </div>

                    </#list>
                </div>

            </div>
            <!-- /.container-fluid -->

        </div>
        <!-- End of Main Content -->

        <!-- Footer -->
        <#include "includes/footer.ftlh"/>
        <!-- End of Footer -->

    </div>
    <!-- End of Content Wrapper -->

</div>
<!-- End of Page Wrapper -->

<!-- Scroll to Top Button-->
<a class="scroll-to-top rounded" href="#page-top">
    <i class="fas fa-angle-up"></i>
</a>


<!-- Bootstrap core JavaScript-->
<script src="/vendor/jquery/jquery.min.js"></script>
<script src="/vendor/bootstrap/js/bootstrap.bundle.min.js"></script>

<!-- Core plugin JavaScript-->
<script src="/vendor/jquery-easing/jquery.easing.min.js"></script>

<!-- Custom scripts for all pages-->
<script src="/js/sb-admin-2.min.js"></script>
<script src="vendor/datatables/jquery.dataTables.min.js"></script>
<script src="vendor/datatables/dataTables.bootstrap4.min.js"></script>
<script type="text/javascript">
    // Call the dataTables jQuery plugin
    $(document).ready(function () {
        $('#dataTable').DataTable();
    });
</script>
</body>

</html>
